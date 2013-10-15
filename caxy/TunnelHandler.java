package caxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.StringTokenizer;

class TunnelHandler {
	PktInpChannel     pktStream;
	WrapHdr           wHdr;
	ByteBuffer        buf;
	InetSocketAddress [] udp_dst;

	INSACache         insaCache;

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

	protected TunnelHandler(PktInpChannel pktStream_in)
	{
		pktStream = pktStream_in;
		wHdr      = new WrapHdr();
		buf       = ByteBuffer.allocate(TCP_BUFSZ);
		udp_dst   = new InetSocketAddress[0];
		insaCache = new INSACache();
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
	int l   = sa.length;
	int idx;
	int i,nl;
	InetSocketAddress []buf = new InetSocketAddress[l];

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

	public void addDstAddress(String s, int port)
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
				addDstAddress( sa );
			}
		}
	}

	public void addDstAddresses(String addresses, int defaultPort)
		throws java.lang.NumberFormatException
	{
	StringTokenizer st = new StringTokenizer(addresses);
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


	public void handleStream(boolean inside, int debug)
		throws IOException, WrapHdr.CaxyBadVersionException, PktInpChannel.IncompleteBufferReadException
	{
	int               i, nCa, opos = 0;
	ClntProxy         clnt;
	boolean           need_whdr_dump = (debug & CaxyConst.DEBUG_TCP) != 0;

		wHdr.read(pktStream);

		nCa = wHdr.get_n_ca();

		buf.clear();
		for ( i = 0; i < nCa; i++ ) {

			opos = buf.position();

				
			CaPkt.read(pktStream, buf);

			if ( (debug & CaxyConst.DEBUG_TCP) != 0 ) {
				int npos = buf.position();
				CaPkt caPkt;
				buf.position(opos);
				caPkt = CaPkt.get(buf);
				if ( (caPkt.get_m_cmmd() != CaPkt.CA_PROTO_RSRV_IS_UP) || (debug & CaxyConst.DEBUG_NOB) == 0 ) {
					if ( need_whdr_dump ) {
						wHdr.dump( debug );
						need_whdr_dump = false;
					}
					System.err.println("TCP: reading p #" + i + " out of " + nCa);
					caPkt.dump( debug );
				}
				buf.position(npos);
			}
		}
		buf.flip();

		if ( ! inside ) { /* protect 'udp_dst' array */
			synchronized ( this ) {
				udp_dst[0] = insaCache.get( wHdr.get_caddr(), wHdr.get_cport());
			}
			clnt       = ClntProxy.get( CaxyConst.INADDR_ANY, 0, 0 );
		} else {
			clnt       = ClntProxy.get( wHdr.get_caddr(), wHdr.get_cport(), 0 );
		}

		synchronized (this ) { /* protect 'udp_dst' array */
			for ( i = 0; i < udp_dst.length; i++ ) {
				/* if whdr has not been dumped yet then there were only beacons */
				if ( (debug & CaxyConst.DEBUG_TCP) != 0 && ! need_whdr_dump ) {
					System.err.println("Sending UDP to: " + udp_dst[i]);
				}
				clnt.putBuf(buf, udp_dst[i]);
			}
		}
	}
}