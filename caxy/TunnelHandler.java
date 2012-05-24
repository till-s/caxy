package caxy;

import java.nio.ByteBuffer;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.lang.Runtime;

import gnu.getopt.Getopt;

class TunnelHandler {
	PktChannel        pktStream;
	WrapHdr           wHdr;
	ByteBuffer        buf;
	InetSocketAddress [] udp_dst;

	INSACache         insaCache;

	static final int  TCP_BUFSZ = 10000;

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

	protected TunnelHandler(PktChannel pktStream_in)
	{
		pktStream = pktStream_in;
		wHdr      = new WrapHdr();
		buf       = ByteBuffer.allocate(TCP_BUFSZ);
		udp_dst   = new InetSocketAddress[0];
		insaCache = new INSACache();
	}

	protected TunnelHandler(int port)
		throws IOException
	{
		this ( new PktChannel( SocketChannel.open( new InetSocketAddress(port) ) ) );
	}

	protected TunnelHandler(String name)
		throws FileNotFoundException
	{
		this (new PktChannel( (new FileInputStream(name)).getChannel()) );
	}

	protected TunnelHandler() {
		this (new PktChannel((new FileInputStream(java.io.FileDescriptor.in)).getChannel()) );
	}

	public synchronized void addDstAddress(String s, int port)
		throws java.lang.NumberFormatException
	{
	StringTokenizer   st = new StringTokenizer(s,":");
	InetSocketAddress sa;
		if ( st.countTokens() > 0 ) {
			String host = st.nextToken();
			if ( st.hasMoreTokens() ) {
				port = Integer.parseInt( st.nextToken() );
			}
			sa = new InetSocketAddress(host, port);
			if ( sa.isUnresolved() ) {
				System.err.println("Ignoring unresolved address: "+host+":"+port);
			} else {
				udp_dst = Arrays.copyOf(udp_dst, udp_dst.length + 1);
				udp_dst[udp_dst.length-1] = sa;
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

	public static int getIntEnv(String env_var, int defltVal)
	{
	String str;
		if ( (str = System.getenv(env_var)) != null ) {
			try {
				return Integer.parseInt(str);
			} catch ( java.lang.NumberFormatException e ) {
				System.err.println("Unable to parse EPICS_CA_SERVER_PORT env-var");
				System.exit(1);
			}
		}
		return defltVal;
	}

	public void handleStream(boolean inside, int debug)
		throws IOException, WrapHdr.CaxyBadVersionException, PktChannel.IncompleteBufferReadException
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

		if ( ! inside ) {
			udp_dst[0] = insaCache.get( wHdr.get_caddr(), wHdr.get_cport());
			clnt       = ClntProxy.get( CaxyConst.INADDR_ANY, 0, 0 );
		} else {
			clnt       = ClntProxy.get( wHdr.get_caddr(), wHdr.get_cport(), 0 );
		}

		for ( i = 0; i < udp_dst.length; i++ ) {
			/* if whdr has not been dumped yet then there were only beacons */
			if ( (debug & CaxyConst.DEBUG_TCP) != 0 && ! need_whdr_dump ) {
				System.err.println("Sending UDP to: " + udp_dst[i]);
			}
			clnt.putBuf(buf, udp_dst[i]);
		}
	}

	public static void main(String [] args)
		throws FileNotFoundException, IOException
	{
	PktChannel            inStrm, outStrm;
	TunnelHandler         tunlHdlr;
	int                   tunnel_port = CaxyConst.CA_PORT_BASE;
	int                   server_port;
	int                   rpeatr_port;
	int                   debug  = 0;
	WrapHdr               wHdr   = new WrapHdr();
	Getopt                g      = new Getopt("caxyj", args, "a:d:hIp:");
	int                   opt;
	boolean               inside = false;
	String              []alist  = new String[0];
	String                str;

		while ( (opt = g.getopt()) > 0 ) {
			switch ( opt ) {
				case 'a':
					alist = Arrays.copyOf( alist, alist.length + 1 );
					alist[alist.length - 1] = g.getOptarg();
				break;
	
				case 'd':
					try {
						debug = Integer.parseInt(g.getOptarg());
					} catch ( java.lang.NumberFormatException e ) {
						System.err.println("Illegal argument to -d; must be numerical");
						System.exit(1);
					}
				break;

				case 'h':
					usage("caxy");
					System.exit(0);
				break;

				case 'p':
					try {
						tunnel_port = Integer.parseInt(g.getOptarg());
						if ( tunnel_port < 0 || tunnel_port > 65535 )
							throw new java.lang.NumberFormatException();
					} catch ( java.lang.NumberFormatException e ) {
						System.err.println("Illegal argument to -p; must be port in range 0..65535");
						System.exit(1);
					}
				break;

				case 'I':
					inside = true;
				break;

				default:
					System.err.println("Unknown option '" + (char)opt + "': ignoring");
				break;
			}
		}

		server_port = getIntEnv("EPICS_CA_SERVER_PORT",   CaxyConst.CA_SERVER_PORT);
		rpeatr_port = getIntEnv("EPICS_CA_REPEATER_PORT", CaxyConst.CA_REPEATER_PORT);

		if ( 0 == tunnel_port ) {
			if ( g.getOptind() < args.length ) {
				String [] cmd_args = Arrays.copyOfRange( args, g.getOptind(), args.length );
				Process   p = Runtime.getRuntime().exec(cmd_args);
				outStrm = new PktChannel((ByteChannel)Channels.newChannel(p.getOutputStream()));
				inStrm  = new PktChannel((ByteChannel)Channels.newChannel(p.getInputStream()));
			} else {
				outStrm = PktChannel.getStdout();
				inStrm  = PktChannel.getStdin();
			}
		} else {
			try {
				inStrm = outStrm = PktChannel.open(inside, tunnel_port);
			} catch (IOException e) {
				System.err.println("Unable to create TCP channel (on port " + tunnel_port + "): " + e);
				throw(e);
			}
		}

		ClntProxy.initClass( outStrm, debug );

		try {

			tunlHdlr = new TunnelHandler( inStrm );

			if ( inside ) {
				int i;
				if ( (str = System.getenv("EPICS_CA_ADDR_LIST")) != null ) {
					alist = Arrays.copyOf( alist, alist.length + 1 );
					alist[alist.length-1] = str;
					str   = null;
				}
				for (i=0; i<alist.length; i++) {
					tunlHdlr.addDstAddresses(alist[i], server_port);
				}

				/* read an initial packet which tells us what repeater port the 'outside' is using */
				wHdr.read( inStrm );
				
				new RepProxy( wHdr.get_cport(), rpeatr_port);

			} else {

				tunlHdlr.addDstAddress("0.0.0.0",0);

				/* Send initial packet (OUTSIDE only) */
				wHdr.fill( 0, CaxyConst.INADDR_ANY, CaxyConst.INADDR_ANY, 0, CaxyConst.CA_REPEATER_PORT );
				outStrm.putPkt( wHdr, null );
	
				ClntProxy.get( CaxyConst.INADDR_ANY, 0, server_port );
			}

			wHdr  = null;
			alist = null;

			if ( tunlHdlr.udp_dst.length == 0 ) {
				System.err.println("Must set EPICS_CA_ADDR_LIST or use '-a' in '-I' mode\n");
				System.exit(1);
			}
		
			while ( true ) {
				tunlHdlr.handleStream(inside, debug);
			}
		} catch (IOException e) {
			e.printStackTrace(System.err);
			System.err.println("Broken connection?");
		} catch (PktChannel.IncompleteBufferReadException e) {
			e.printStackTrace(System.err);
			System.err.println("Broken connection?");
		} catch (WrapHdr.CaxyBadVersionException e) {
			System.err.println("Bad CATUN protocol version: " + e.badVersion);
			e.printStackTrace(System.err);
		} catch ( Throwable e ) {
			e.printStackTrace(System.err);
		}
		System.exit(1);
	}

	static void usage(String nm)
	{

	System.err.format( "Usage: %s [-h] [-I -a addr_list] [-d debug_flags] [-p tunnel_port]\n\n", nm);

	System.err.format( "  OPTIONS:\n\n");

    System.err.format( "       -I             Run in 'inside' mode as a proxy for CA clients\n");
    System.err.format( "                      on the 'outside'.\n\n");


    System.err.format( "       -p tunnel_port TCP port to use for the tunnel (defaults to: %d).\n", CaxyConst.CA_SERVER_PORT);
	System.err.format( "                      This flag is available on both, the 'inside' and the 'outside'.\n");
	System.err.format( "                      The setting MUST match the port numbers forwarded by SSH.\n");
	System.err.format( "                      E.g., if you use an explicit ssh tunnel\n\n");
	System.err.format( "                         ssh -L <outs_port>:localhost:<ins_port> ins_host\n\n");
	System.err.format( "                      then on the outside you must execute\n\n");
	System.err.format( "                         %s -p <outs_port>\n\n", nm);
	System.err.format( "                      and on the inside\n\n");
	System.err.format( "                         %s -I -p <ins_port>\n\n", nm);
	System.err.format( "                      OTOH, if you 'proxify' the tunnel also via ssh's -D\n");
	System.err.format( "                      option (CAVEAT: this doesn't seem to work with 'tsocks'\n");
	System.err.format( "                      but it does with 'dante') then the 'tunnel_port' on either\n");
	System.err.format( "                      side must be the same.\n\n");
	System.err.format( "                      NOTE: you may say '-p0' (a zero port number) in which case\n");
	System.err.format( "                            %s will use STDIO for tunnel traffic. You may set\n", nm);
	System.err.format( "                            up pipes so that %s on either side communicate via\n", nm);
	System.err.format( "                            SSH's stdio.\n\n");

    System.err.format( "       -a addr_list   White-space separated list of 'addr[:port]' items. This option\n");
    System.err.format( "                      has the same effect as (and is augmented by) the env-var\n");
    System.err.format( "                      EPICS_CA_ADDR_LIST. Only effective if -I is given. CA search\n");
    System.err.format( "                      requests from the outside-client are forwarded to all members\n");
    System.err.format( "                      on the combined list on the 'inside' network.\n");
    System.err.format( "                      Multiple -a options may be given.\n\n");

    System.err.format( "       -d debug_flags Enable debug messages (on stderr). 'debug_flags' is a bitset\n");
    System.err.format( "                      of switches:\n");
    System.err.format( "                         1: dump incoming UDP frames\n");
    System.err.format( "                         2: dump incoming TCP frames\n\n");
    System.err.format( "                         8: omit CA beacon messages\n\n");
	if ( false ) {
	/* not supported (yet) */
    System.err.format( "       -n             Do not do any DNS lookup when dumping IP addresses.\n");
    System.err.format( "                      Has only an effect on info printed by -d. Use this\n");
    System.err.format( "                      option if the program executes very slowly due to DNS\n");
    System.err.format( "                      problems or general slow-ness.\n\n");
	}

    System.err.format( "       -h             Print this information.\n\n\n");


	System.err.format( "  ENVIRONMENT:\n\n");

    System.err.format( "       EPICS_CA_SERVER_PORT\n");
    System.err.format( "                      UDP port where %s listens for incoming requests on the\n", nm);
    System.err.format( "                      'outside'. In 'inside' mode this defines the port number\n");
    System.err.format( "                      where search requests are sent to if any member of\n");
    System.err.format( "                      the address-list (-a and EPICS_CA_ADDR_LIST) does not\n");
    System.err.format( "                      explicitly specify a port number. The default value is %d.\n\n", CaxyConst.CA_PORT_BASE);
	System.err.format( "                      NOTE: it is perfectly possible to use different settings\n");
	System.err.format( "                      for EPICS_CA_SERVER_PORT on the inside and outside.\n\n");

    System.err.format( "       EPICS_CA_REPEATER_PORT\n");
    System.err.format( "                      UDP port where %s repeater subscriptions ('inside' mode) and\n", nm);
	System.err.format( "                      beacons ('outside' mode), respectively, are sent to.\n");
	System.err.format( "                      The default value for the repeater port is %d.\n\n", CaxyConst.CA_REPEATER_PORT);

	System.err.format( "                      NOTE: it is perfectly possible to use different settings\n");
	System.err.format( "                      for EPICS_CA_SERVER_PORT on the inside and outside.\n\n");

    System.err.format( "       EPICS_CA_ADDR_LIST\n");
    System.err.format( "                      White-space separated list of <address>[:<port>] items defining\n");
    System.err.format( "                      all addresses where the 'inside' proxy should send CA search\n");
    System.err.format( "                      requests (unused in 'outside' mode). Consult EPICS documentation\n");
    System.err.format( "                      for more details. Note that <address> may be a DNS name or plain\n");
    System.err.format( "                      IP address. If no port number is given then the ('inside') value\n");
    System.err.format( "                      of EPICS_CA_SERVER_PORT is used. The contents of this variable\n");
    System.err.format( "                      are appended to all '-a' options.\n");
	}
}
