package caxy;

import java.nio.ByteBuffer;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.io.IOException;
import java.util.LinkedList;
import java.util.ListIterator;

class ClntProxy extends Thread {
	protected static  PktOutChannel     pktStream;
	protected         DatagramChannel   udpChnl;
	protected         ByteBuffer        buf;
	protected         WrapHdr           wHdr;
	protected         boolean           inside;
	protected         static int        debug;

	static final int  UDP_BUFSZ = 10000;
	
	public static final int OK  = 0;
	public static final boolean INSIDE  = true;
	public static final boolean OUTSIDE = false;

	public static final int MYADDR_IDX = 0;
	public static final int MYPORT_IDX = 1;

	protected         int             []abuf;

	protected         int               pxy_addr, pxy_port;

	protected static final int          MAX_CLNTS = 5;

	protected static int                num_clnts = 0;

	protected static LinkedList<ClntProxy> list = new LinkedList<ClntProxy>();


	public static synchronized void initClass(PktOutChannel pktStream_in, int debug_in)
	{
		pktStream = pktStream_in;
		debug     = debug_in;
	}

	public void start(Class<? extends ClntProxy> c)
	{
		/* Start only if we are an instance of 'c'.
		 * If we are a superclass then start() should
		 * be executed from the subclass constructor.
		 */
		if ( this.getClass().equals( c ) )
			super.start();
	}

	public ClntProxy(int port)
		throws IOException
	
	{
		this(OUTSIDE, 0, 0, port);
	}

	protected ClntProxy(boolean inside_in, int clnt_address, int clnt_port, int udp_port)
		throws IOException
	{
		if ( null == pktStream ) {
			throw new NullPointerException();
		}
		pxy_addr = clnt_address;
		pxy_port = clnt_port;
		buf      = ByteBuffer.allocate(UDP_BUFSZ);
		wHdr     = new WrapHdr();
		udpChnl  = open( udp_port );
		inside   = inside_in;

		abuf     = new int[2];

		start( ClntProxy.class );
	}

	public ClntProxy(int clnt_address, int clnt_port)
		throws IOException
	{
		this(INSIDE, clnt_address, clnt_port, 0);
	}

	public int putBuf(ByteBuffer b, SocketAddress peer)
		throws IOException
	{
		b.rewind();
		return udpChnl.send(b, peer);
	}

	boolean
	handleUdp()
		throws IOException, PktOutChannel.IncompleteBufferWrittenException
	{
	InetSocketAddress udp_src;
	int               skip, nCa;
	int               udp_src_a, udp_src_p;
	boolean           rep_seen = false;
	boolean           need_whdr_dump = (debug & CaxyConst.DEBUG_UDP) != 0;
	boolean           need_whdr = true;

		buf.clear();

		udp_src = (InetSocketAddress)udpChnl.receive(buf);

		buf.flip();

		udp_src_a = InetConv.inet2int(udp_src.getAddress());
		udp_src_p = udp_src.getPort();

		for ( nCa = 0; buf.remaining() > 0; ) {
			CaPkt caPkt = CaPkt.get(buf);

			skip = inside ? caPkt.hack(udp_src_a, udp_src_p, false, false) : 0;

			if ( 0 == skip )
				nCa++;
		}
		buf.rewind();

		if ( inside ) {
			getMyAddr(abuf);
			wHdr.fill(nCa,
			          udp_src_a,
			          abuf[MYADDR_IDX],
			          udp_src_p,
			          abuf[MYPORT_IDX]);
		} else {
			wHdr.fill(nCa,
			          CaxyConst.INADDR_ANY,
					  udp_src_a,
					  0,
					  udp_src_p);
		}

		buf.rewind();

		synchronized( pktStream ) {

			while ( buf.remaining() > 0 ) {

				CaPkt caPkt = CaPkt.get(buf);

				skip = inside ? caPkt.hack(udp_src_a, udp_src_p, false, true) : 0;

				if ( (debug & CaxyConst.DEBUG_UDP) != 0 ) {
					if ( (caPkt.get_m_cmmd() != CaPkt.CA_PROTO_RSRV_IS_UP) || (debug & CaxyConst.DEBUG_NOB) == 0 ) {
						if ( need_whdr_dump ) {
							System.err.format("UDP datagram received (len %d)", buf.limit());
							System.err.println(" from: " + udp_src);
							wHdr.dump(debug);
							need_whdr_dump = false;
						}

						if ( skip != 0 )
							System.err.println("vvvvv FOLLOWING MESSAGE WILL BE SKIPPED vvvvv");
						caPkt.dump( debug );
					}
				}

				rep_seen = rep_seen || ( CaPkt.REPEATER_CONFIRM == skip );

				if ( 0 == skip ) {

					if ( need_whdr ) {
						wHdr.out(pktStream, null);
						need_whdr = false;
					}

					caPkt.out(pktStream, null);
				}
			}

		}

		return rep_seen;
	}

	public void run() {
		try {
			while ( true ) {
				handleUdp();
			}
		} catch (Throwable e) {
			e.printStackTrace();
		} finally {
			System.exit(1);
		}
	}

	public static DatagramChannel open(int port)
		throws IOException
	{
	DatagramChannel udpChnl;

			udpChnl = DatagramChannel.open();
			udpChnl.socket().bind( 0 == port ? null : new InetSocketAddress( port ) );
			udpChnl.socket().setBroadcast( true );
			udpChnl.socket().setReuseAddress( true );
			return udpChnl;
	}

	public static DatagramChannel open()
		throws IOException
	{
		return open( CaxyConst.CA_SERVER_PORT );
	}

	protected synchronized void setMyAddr(int clnt_address, int clnt_port)
	{
		pxy_addr = clnt_address;
		pxy_port = clnt_port;
	}

	protected synchronized void getMyAddr(int []addr_out)
	{
		addr_out[MYADDR_IDX] = pxy_addr;
		addr_out[MYPORT_IDX] = pxy_port;
	}

	public static ClntProxy get(int clnt_address, int clnt_port, int port)
		throws IOException
	{
	ClntProxy    clnt;

		synchronized( list ) {
			ListIterator<ClntProxy> i = list.listIterator();
			while ( i.hasNext() ) {
				clnt = i.next();
				if ( clnt.pxy_addr == clnt_address && clnt.pxy_port == clnt_port ) {
					if ( 0 != i.previousIndex() ) {
						/* bring to the front */
						i.remove();
						list.add( clnt );
					} else {
						/* no need to rearrange the list */
					}
					return clnt;
				}
			}

			if ( num_clnts < MAX_CLNTS ) { 
				clnt = new ClntProxy( 0 == port ? INSIDE : OUTSIDE, clnt_address, clnt_port, port );
				num_clnts++;
			} else {
				clnt = (ClntProxy)list.removeLast();
				clnt.setMyAddr( clnt_address, clnt_port );
			}

			list.add(clnt);
		}
		return clnt;
	}
}
