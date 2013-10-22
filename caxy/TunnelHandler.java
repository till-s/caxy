package caxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.StringTokenizer;


public class TunnelHandler implements Runnable {
	
	public interface OnTunnelStateChangeListener {
		public void onTunnelStateChange(boolean up);
	}

	PktInpChannel     pktStream;
	WrapHdr           wHdr;
	ByteBuffer        buf;
	InetSocketAddress [] udp_dst;
	ClntProxyPool     proxyPool;

	INSACache         insaCache;

	Env               env;
	
	volatile OnTunnelStateChangeListener onStateChange = null;
	
	public void setOnTunnelStateChangeListener(OnTunnelStateChangeListener l) {
			onStateChange = l;
	}

	static final int  TCP_BUFSZ = 10000;


	public static final int TUNNEL_PORT_DFLT = 0;

	class INSAEntry {
		protected int                     addr, port;
		protected InetSocketAddress       sa;

		public void set(int addr_in, int port_in)
		{
			addr = addr_in; port = port_in;
			sa   = new InetSocketAddress( InetConv.int2inet(addr), port );
		}

		public InetSocketAddress update( int addr_in, int port_in )
		{
			if ( addr_in != addr || port_in != port ) {
				set(addr_in, port_in);
			}
			return sa;
		}

		public INSAEntry(int addr, int port)
		{
			set(addr, port);
		}
	}

	class INSACache {
		protected static final int    LD_SZ = 4;
		protected INSAEntry  []cache;

		INSACache()
		{
		int i;
			cache = new INSAEntry[1<<LD_SZ];
			for ( i=0; i<cache.length; i++ )
				cache[i] = null;
		}

		protected int hash(int addr, int port)
		{
		int m = -1640531535; /* == 2654435761 */
		int h = ((addr * m) >> (32-16)) + (port << 16) ;
			h = (h * m) >> (32 - LD_SZ);
			return (h & ((1<<LD_SZ) - 1));
		}

		public InetSocketAddress get(int addr, int port)
		{
		int h = hash(addr,port);

			if ( null == cache[h] ) {
				cache[h] = new INSAEntry( addr, port );
			}
			return cache[h].update(addr, port);
		}
	}

	public TunnelHandler(ClntProxyPool proxyPool_in, PktInpChannel pktStream_in, Env env_in)
	{
		env       = env_in;
		udp_dst   = env.get();
		if ( null == udp_dst )
			udp_dst = new InetSocketAddress[0];
		proxyPool = proxyPool_in;
		pktStream = pktStream_in;
		wHdr      = new WrapHdr();
		buf       = ByteBuffer.allocate(TCP_BUFSZ);
		insaCache = new INSACache();
	}

	public void handleStream()
		throws IOException, WrapHdr.CaxyBadVersionException,
		       PktInpChannel.IncompleteBufferReadException,
		       ClntProxyPoolShutdownException
	{
	int               i, nCa, opos = 0;
	ClntProxyPool.ClntProxy clnt;
	boolean           need_whdr_dump = (env.debug & CaxyConst.DEBUG_TCP) != 0;

		wHdr.read(pktStream);

		nCa = wHdr.get_n_ca();

		buf.clear();
		for ( i = 0; i < nCa; i++ ) {

			opos = buf.position();

				
			CaPkt.read(pktStream, buf);

			if ( (env.debug & CaxyConst.DEBUG_TCP) != 0 ) {
				int npos = buf.position();
				CaPkt caPkt;
				buf.position(opos);
				caPkt = CaPkt.get(buf);
				if ( (caPkt.get_m_cmmd() != CaPkt.CA_PROTO_RSRV_IS_UP) || (env.debug & CaxyConst.DEBUG_NOB) == 0 ) {
					if ( need_whdr_dump ) {
						wHdr.dump( env.debug );
						need_whdr_dump = false;
					}
					System.err.println("TCP: reading p #" + i + " out of " + nCa);
					caPkt.dump( env.debug );
				}
				buf.position(npos);
			}
		}
		buf.flip();

		if ( ! env.inside ) { /* protect 'udp_dst' array */
			synchronized ( this ) {
				udp_dst[0] = insaCache.get( wHdr.get_caddr(), wHdr.get_cport());
			}
			clnt       = proxyPool.get( CaxyConst.INADDR_ANY, 0, 0 );
		} else {
			clnt       = proxyPool.get( wHdr.get_caddr(), wHdr.get_cport(), 0 );
		}

		synchronized (this ) { /* protect 'udp_dst' array */
			for ( i = 0; i < udp_dst.length; i++ ) {
				/* if whdr has not been dumped yet then there were only beacons */
				if ( (env.debug & CaxyConst.DEBUG_TCP) != 0 && ! need_whdr_dump ) {
					System.err.println("Sending UDP to: " + udp_dst[i]);
				}
				clnt.putBuf(buf, udp_dst[i]);
			}
		}
	}

	public void shutdown() {
		// closing all the IO channels should cause all the threads to die...
		try {
			pktStream.close();
		} catch (IOException e) {
		}
		proxyPool.shutdown();
		
		OnTunnelStateChangeListener l = onStateChange;
		if ( null != l)
			l.onTunnelStateChange( false );
	}

	public void execute()
		throws IOException, PktInpChannel.IncompleteBufferReadException,
		       WrapHdr.CaxyBadVersionException, PktOutChannel.IncompleteBufferWrittenException,
			   ClntProxyPoolShutdownException
	{
		try {
			WrapHdr wHdr = new WrapHdr();
			if ( env.inside ) {
				// read an initial packet which tells us what repeater port the 'outside' is using 
				wHdr.read( pktStream );
				
				// send empty header to let them know we're ready...
				proxyPool.sendRepPortInfo( 0 );
				
				new RepProxy(proxyPool, wHdr.get_cport(), env.repeater_port);
			} else {
				/* Send initial packet (OUTSIDE only) */
				proxyPool.sendRepPortInfo( env.repeater_port );
				
				// wait for answer (empty WrapHdr) from the inside
				wHdr.read( pktStream );

				proxyPool.get( CaxyConst.INADDR_ANY, 0, env.server_port );
			}
			wHdr  = null;
			
			// now the tunnel is up...
			OnTunnelStateChangeListener l = onStateChange;
			if ( null != l)
				l.onTunnelStateChange( true );

			while ( true ) {
				handleStream();
			}

		} finally {
			shutdown();
		}
	}

	public void run() {
		if ( 0 != ( env.debug & CaxyConst.DEBUG_THREAD) ) {
			System.err.println("TunnelHandler Thread Start");
		}

		try {
			execute();
		} catch ( IOException e ) {
		} catch ( PktInpChannel.IncompleteBufferReadException e ) {
		} catch ( PktOutChannel.IncompleteBufferWrittenException e ) {
		} catch ( WrapHdr.CaxyBadVersionException e ) {
		} catch ( ClntProxyPoolShutdownException e ) {
		} finally {
			if ( 0 != ( env.debug & CaxyConst.DEBUG_THREAD) ) {
				System.err.println("TunnelHandler Thread Exit");
			}
		}
	}
	
	public static class Env {
		final boolean inside;
		final int     server_port;
		final int     repeater_port;
		final int     debug;

		private InetSocketAddress udp_dst[] = new InetSocketAddress[0];

		public Env(boolean inside, int server_port, int repeater_port, int debug)
		{
			this.inside        = inside;
			this.server_port   = server_port;
			this.repeater_port = repeater_port;
			this.debug         = debug;
		}

		/* extend array by 'n' slots; not synchronized because caller is */
		protected int arrext(int n)
		{
		int i;
		InetSocketAddress [] oa = udp_dst;
		int rval                = udp_dst.length;

			udp_dst = new InetSocketAddress[rval + n];
			/* Avoid Arrays.copyOf - not in java 1.4 */
			for ( i=0; i<oa.length; i++ ) {
				udp_dst[i] = oa[i];
			}
			return rval;
		}

		/* Not efficient but only done once, during initialization */
		protected boolean present(InetSocketAddress sa)
		{
		int i;
			if ( null == sa )
				return true;

			for ( i=0; i<udp_dst.length; i++ ) {
				if ( udp_dst[i].equals( sa ) )
					return true;
			}
			return false;
		}

		public synchronized void addDstAddress(InetSocketAddress sa)
		{
			/* Only add if not there already */
			if ( ! present( sa ) ) {
			int idx = arrext(1);
				udp_dst[idx] = sa;
			}
		}

		public synchronized void addDstAddresses(InetAddress []sa, int port)
		{
		int l;
		int idx;
		int i,nl;
		InetSocketAddress []buf;

			if ( null == sa )
				return;

			l   = sa.length;
			buf = new InetSocketAddress[l];

			for ( i=0, nl=l; i<l; i++ ) {
				if ( (present( buf[i] = new InetSocketAddress( sa[i], port ) )) ) {
					buf[i] = null;
					nl--;
				}
			}

			idx = arrext(nl);

			for ( i = 0, nl=0; i < l; i++ ) {
				if ( null != buf[i] ) {
					udp_dst[idx + nl++] = buf[i];
				}
			}
		}

		public static InetSocketAddress cvtAddress(String s, int port)
			throws java.lang.NumberFormatException
		{
		StringTokenizer   st = new StringTokenizer(s,":");
		InetSocketAddress sa;
			if ( st.countTokens() > 0 ) {
				String host = st.nextToken();
				if ( st.hasMoreTokens() ) {
					port = Integer.decode( st.nextToken() ).intValue();
				}
				sa = new InetSocketAddress(host, port);
				if ( sa.isUnresolved() ) {
					System.err.println("Ignoring unresolved address: "+host+":"+port);
				} else {
					return sa;
				}
			}
			return null;
		}

		public void addDstAddress(String s, int port)
			throws java.lang.NumberFormatException
		{
		InetSocketAddress sa = cvtAddress(s, port);
			if ( null != sa )
				addDstAddress( sa );
		}

		public void addDstAddresses(String addresses, int defaultPort)
			throws java.lang.NumberFormatException
		{
		StringTokenizer     st = new StringTokenizer(addresses);
			while ( st.hasMoreTokens() ) {
				addDstAddress( st.nextToken(), defaultPort );
			}
		}

		public synchronized void dumpDstAddresses()
		{
		int i;
			System.err.println("CA Address list:");
			for ( i=0; i<udp_dst.length; i++ ) {
				System.err.println("  "+udp_dst[i]);
			}
		}

		public synchronized InetSocketAddress [] get()
		{
			return udp_dst.clone();
		}

		public synchronized int getLength()
		{
			return udp_dst.length;
		}
	}

}
