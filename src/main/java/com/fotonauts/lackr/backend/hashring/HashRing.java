package com.fotonauts.lackr.backend.hashring;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fotonauts.commons.RapportrInterface;
import com.fotonauts.lackr.BaseGatewayMetrics;
import com.fotonauts.lackr.HttpDirectorInterface;
import com.fotonauts.lackr.HttpHost;
import com.fotonauts.lackr.backend.LackrBackendRequest;

public class HashRing implements HttpDirectorInterface {

	static Logger log = LoggerFactory.getLogger(RingHost.class);

	@SuppressWarnings("serial")
	public static class NotAvailableException extends Exception {
	};

	int bucketPerHost = 128;
	AtomicInteger up = new AtomicInteger(0);
	RingHost[] hosts;
	private String name;

	private NavigableMap<Integer, RingHost> ring;

	private RapportrInterface rapportrInterface;
	private String[] hostnames;
	private String probeUrl;
    private AtomicBoolean mustStop = new AtomicBoolean(false);
    private Thread proberThread;

	public String getProbeUrl() {
		return probeUrl;
	}

	public void setProbeUrl(String probeUrl) {
		this.probeUrl = probeUrl;
	}

	public HashRing() {
	}

	public void setHostnames(String hostnames) {
		this.hostnames = hostnames.split(",");
	}

	public void setHosts(RingHost[] hosts) {
		this.hosts = hosts;
	}

	public HashRing(String... backends) {
		hostnames = backends;
		init();
	}

	public HashRing(RingHost... backends) {
		hosts = backends;
		init();
	}

	public void init() {
		if (hosts == null && hostnames != null) {
			hosts = new RingHost[hostnames.length];
			for (int i = 0; i < hostnames.length; i++) {
				hosts[i] = new RingHost(rapportrInterface, hostnames[i], probeUrl);
			}
		}
		ring = new TreeMap<Integer, RingHost>();
		for (RingHost h : hosts) {
			h.setRing(this);
			Random random = new Random(h.getHostname().hashCode());
			for (int i = 0; i < bucketPerHost; i++) {
				ring.put(random.nextInt(), h);
			}
		}
        for (RingHost h : hosts)
            h.start();
		up.set(hosts.length);
		proberThread = new Thread() {
			public void run() {
				while (!mustStop.get()) {
					for (RingHost h : hosts) {
						h.probe();
					}
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
					}
				}
			};
		};
		if(hostnames != null) {
    		StringBuilder builder = new StringBuilder();
            for(String hostname: hostnames) {
                if(!builder.toString().isEmpty())
                    builder.append("_");
                builder.append(hostname);
            }
            name = builder.toString();
		}
		proberThread.setName("HashRingProber for " + name);
		proberThread.setDaemon(true);
		proberThread.start();
	}

	public boolean up() {
		return up.intValue() > 0;
	}

	public RingHost getHostFor(String value) throws NotAvailableException {
		if (!up())
			throw new NotAvailableException();
		MessageDigest m = null;
		try {
			m = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			// nope.
		}
		m.update(value.getBytes());
		ByteBuffer bb = ByteBuffer.wrap(m.digest());
		SortedMap<Integer, RingHost> tail = ring.tailMap(bb.getInt());
		for (Entry<Integer, RingHost> entry : tail.entrySet()) {
			if (entry.getValue().isUp())
				return entry.getValue();
		}
		for (Entry<Integer, RingHost> entry : ring.entrySet()) {
			if (entry.getValue().isUp())
				return entry.getValue();
		}
		throw new NotAvailableException();
	}

	public void refreshStatus() {
		int ups = 0;
		for (RingHost host : hosts) {
			if (host.isUp())
				ups++;
		}
		up.set(ups);
		String message = "Ring has " + up.get() + " backend up among " + hosts.length + ".";
		log.warn(message);
		if (rapportrInterface != null)
			rapportrInterface.warnMessage(message, null);
	}

	public NavigableMap<Integer, RingHost> getRing() {
		return ring;

	}

	public BaseGatewayMetrics[] getGateways() {
		return hosts;
	}

	public RingHost[] getHosts() {
        return hosts;
    }

	@Override
	public HttpHost getHostFor(LackrBackendRequest request) throws NotAvailableException {
		return getHostFor(request.getQuery());
	}

	public RapportrInterface getRapportrInterface() {
		return rapportrInterface;
	}

	public void setRapportrInterface(RapportrInterface rapportrInterface) {
		this.rapportrInterface = rapportrInterface;
	}

	@Override
	public void dumpStatus(PrintStream ps) {
		Map<RingHost, Long> weights = new HashMap<RingHost, Long>();
		for (RingHost h : getHosts())
			weights.put(h, 0L);
		Entry<Integer, RingHost> previous = null;
		for (Entry<Integer, RingHost> e : getRing().entrySet()) {
			if (previous != null)
				weights.put(previous.getValue(), weights.get(previous.getValue()) + e.getKey() - previous.getKey());
			ps.format("ring-boundary\t%08x\t\n", e.getKey(), e.getValue().getHostname());
			previous = e;
		}
		weights.put(previous.getValue(), weights.get(previous.getValue()) + (getRing().firstKey() - Integer.MIN_VALUE));
		weights.put(previous.getValue(), weights.get(previous.getValue()) + (Integer.MAX_VALUE - getRing().lastKey()));
		for (RingHost h : getHosts()) {
			ps.format("picor-ring-weight\t%s\t%s\t%d\n", h.getHostname(), h.isUp() ? "UP" : "DOWN", weights.get(h));
		}
	}
	
    @Override
    public String getName() {
        return name;
    }

    @Override
    public void stop() throws InterruptedException {
        mustStop.set(true);
        proberThread.join();
    }


}
