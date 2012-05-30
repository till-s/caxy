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
import java.io.OutputStream;
import java.util.Arrays;
import java.util.StringTokenizer;
import java.lang.Runtime;
import java.util.Properties;

import gnu.getopt.Getopt;

class TunnelHandler {
	PktInpChannel     pktStream;
	WrapHdr           wHdr;
	ByteBuffer        buf;
	InetSocketAddress [] udp_dst;

	INSACache         insaCache;

	static final int  TCP_BUFSZ = 10000;

	public static final String name = "caxyj";

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
				System.err.println("Unable to parse "+env_var+" env-var");
				System.exit(1);
			}
		}
		return defltVal;
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
	PktInpChannel         inpStrm;
	PktOutChannel         outStrm;
	TunnelHandler         tunlHdlr;
	int                   tunnel_port = TUNNEL_PORT_DFLT;
	String                env_addrlst = null;
	int                   server_port;
	int                   rpeatr_port;
	int                   debug  = 0;
	WrapHdr               wHdr   = new WrapHdr();
	Getopt                g      = new Getopt(name, args, "a:d:hIJ:p:P:v");
	int                   opt;
	boolean               inside = false;
	String              []alist  = new String[0];
	OutputStream          os     = null;
	CaxyJcaProp           props  = null;
	String                jcaPre = null;
	boolean               use_env= false;

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
					usage(name);
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

				case 'J':
					jcaPre = g.getOptarg();
				break;

				case 'v':
					System.err.println(name+" release: " + CaxyVers.VERSION_STR);
					System.exit(0);
				break;

				default:
					System.err.println("Unknown option '" + (char)opt + "': ignoring");
				break;
			}
		}

		try {
			props = new CaxyJcaProp( jcaPre );
		} catch ( Exception e ) {
			System.err.println("Illegal argument to -P; unable to load properties");
			System.err.println(e);
			System.exit(1);
		} finally {
			jcaPre = null;
		}

		if (     null == props.getProperty( "jca.use_env" )
		      && null == props.getJcaProperty( "jca.use_env" )
              || Boolean.valueOf( props.getProperty( "jca.use_env" ) )
              || Boolean.valueOf( props.getJcaProperty( "jca.use_env" ) )
		   )
		{
			use_env     = true;
			server_port = getIntEnv("EPICS_CA_SERVER_PORT",   CaxyConst.CA_SERVER_PORT);
			rpeatr_port = getIntEnv("EPICS_CA_REPEATER_PORT", CaxyConst.CA_REPEATER_PORT);
			if ( inside ) {
				env_addrlst = System.getenv("EPICS_CA_ADDR_LIST");
			}
		} else {
			server_port = props.getJcaIntProperty( "server_port",   CaxyConst.CA_SERVER_PORT );
			rpeatr_port = props.getJcaIntProperty( "repeater_port", CaxyConst.CA_REPEATER_PORT );
			if ( inside ) {
				env_addrlst = props.getJcaProperty( "addr_list" );
			}
		}

		props = null;

		if ( 0 == tunnel_port ) {
			if ( g.getOptind() < args.length && ! inside ) {
				String [] cmd_args = Arrays.copyOfRange( args, g.getOptind(), args.length );
				Process   p = Runtime.getRuntime().exec(cmd_args);
				outStrm = new PktOutStrmChannel(( (os = p.getOutputStream())));
				inpStrm = new PktInpChannel(Channels.newChannel(p.getInputStream()));
				new Errlog(p.getErrorStream());
			} else {
				outStrm = PktOutChannel.getStdout();
				inpStrm = PktInpChannel.getStdin();
			}
		} else {
			try {
				PktBidChannel bid = new PktBidChannel(inside, tunnel_port);
				inpStrm = bid.getPktInpChannel();
				outStrm = bid.getPktOutChannel();
			} catch (IOException e) {
				System.err.println("Unable to create TCP channel (on port " + tunnel_port + "): " + e);
				throw(e);
			}
		}

		ClntProxy.initClass( outStrm, debug );

		try {

			tunlHdlr = new TunnelHandler( inpStrm );

			if ( inside ) {
				int i;
				if ( null != env_addrlst ) {
					alist = Arrays.copyOf( alist, alist.length + 1 );
					alist[alist.length-1] = env_addrlst;
					env_addrlst           = null;
				}
				for (i=0; i<alist.length; i++) {
					tunlHdlr.addDstAddresses(alist[i], server_port);
				}

				/* read an initial packet which tells us what repeater port the 'outside' is using */
				wHdr.read( inpStrm );
				
				new RepProxy( wHdr.get_cport(), rpeatr_port);

				System.err.println("CAXY -- tunnel now established");

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
				System.err.format("Must set EPICS_CA_ADDR_LIST or use '-a' in '-I' mode (using %s)\n", use_env ? "ENVIRONMENT" : "PROPERTIES");
				System.exit(1);
			}

			while ( true ) {
				tunlHdlr.handleStream(inside, debug);
			}
		} catch (IOException e) {
			e.printStackTrace(System.err);
			System.err.println("Broken connection?");
		} catch (PktInpChannel.IncompleteBufferReadException e) {
			e.printStackTrace(System.err);
			System.err.println("Broken connection?");
		} catch (WrapHdr.CaxyBadVersionException e) {
			System.err.println( e.getMessage() );
			e.printStackTrace(System.err);
		} catch ( Throwable e ) {
			System.err.println( e.getMessage() );
			e.printStackTrace(System.err);
		}
		System.exit(1);
	}

	static void usage(String nm)
	{

	System.err.format( "Usage: %s [-h] [-I -a addr_list] [-d debug_flags] [-p tunnel_port] [-J prefix] [ [--] cmd [ args ] ]\n\n", nm);

	System.err.format( "  OPTIONS:\n\n");

    System.err.format( "       -I             Run in 'inside' mode as a proxy for CA clients\n");
    System.err.format( "                      on the 'outside'.\n\n");


    System.err.format( "       -p tunnel_port TCP port to use for the tunnel (defaults to: %d).\n", TUNNEL_PORT_DFLT);
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
	System.err.format( "                            %s will use STDIO for tunnel traffic. You may want to use\n", nm);
	System.err.format( "                            the '-- cmd [args]' feature (see below) to set up a tunnel\n");
	System.err.format( "                            over SSH's stdio.\n\n");

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

	System.err.format( "       -J             Prefix string when looking up 'JCA' context properties; e.g.,\n");
	System.err.format( "                      'gov.aps.jca.jni.JNIContext' or 'com.cosylab.epics.caj.CAJContext'.\n");
	System.err.format( "                      If no prefix is set or a resource with the give prefix is not found\n");
	System.err.format( "                      then a an attempt using the 'default' prefix 'gov.aps.jca.Context'\n");
	System.err.format( "                      is made.\n\n");

    System.err.format( "       -h             Print this information.\n\n");

    System.err.format( "       -v             Print release information\n\n");

    System.err.format( "       --             STRONGLY RECOMMENDED if <cmd> [<args>] is used. Marks\n");
    System.err.format( "                      the end for %s's option processing. This prevents any\n", nm);
    System.err.format( "                      options given to <cmd> being 'eaten' by caxy\n\n");

    System.err.format( "       <cmd> [<args>] If any extra parameters are given to %s (outside mode\n", nm);
    System.err.format( "                      only) then a new process is spawned, trying to execute <cmd>.\n");
    System.err.format( "                      All subsequent <args> are passed on to this process.\n");
    System.err.format( "                      Most importantly, caxy's stdin/stdout streams are connected\n");
    System.err.format( "                      to the process'es stdout/stdin, respectively. The process'es\n");
    System.err.format( "                      stderr stream is copied (by a dedicated thread) verbatim to\n");
    System.err.format( "                      caxy's stderr.\n");
    System.err.format( "                      This feature is extremely useful to set up a tunnel. <cmd>\n");
    System.err.format( "                      typically launches an SSH session executing an 'inside' version\n");
    System.err.format( "                      of caxy on the 'inside'. Here is an example:\n\n");
    System.err.format( "                        java -jar caxy.jar -- ssh -D 1080 user@insidehost java -jar caxy.jar -I\n\n");

	System.err.println();

	System.err.format( "  ENVIRONMENT:\n\n");

	System.err.format( "       NOTE: Environment variables are ONLY read if the system property 'jca.use_env' is\n");
	System.err.format( "             set to 'true' or unset and if the JCA property (using one of the JCA\n");
	System.err.format( "             prefixes, see '-J' above) is either not set or set to 'true'\n");
	System.err.format( "             If 'use_env' is determined to be 'false' then environment variables are ignored\n");
	System.err.format( "             and JCA properties 'server_port', 'repeater_port' and 'addr_list' are looked up,\n");
	System.err.format( "             respectively. JCA properties have the prefix as given (in order of precedence) by\n");
	System.err.format( "             '-J <prefix>', or the string 'gov.aps.jca.Context' as a fallback.\n");
	System.err.format( "             Properties are first looked up in the user properties (path itself defined by\n");
	System.err.format( "             JCA property 'gov.aps.jca.JCALibrary.properties' or if such a property is not\n");
	System.err.format( "             found then '.JCALibrary/JCALibrary.properties' in the user's home directory\n");
	System.err.format( "             is used). If no user-specific property is found then the system-wide ones\n");
	System.err.format( "             (located in 'lib/JCALibrary.properties' in the java home directory) are consulted\n");
	System.err.format( "             and finally, a set of built-in resources 'JCALibrary.properties'.\n");
	System.err.format( "             This scheme essentially follows what JCA is doing.\n\n");

	System.err.format( "             The features provided by '-J' as well as system and JCA properties are\n");
	System.err.format( "             aimed at easing interoperability with JCA.\n\n");

    System.err.format( "       EPICS_CA_SERVER_PORT\n");
    System.err.format( "                      UDP port where %s listens for incoming requests on the\n", nm);
    System.err.format( "                      'outside'. In 'inside' mode this defines the port number\n");
    System.err.format( "                      where search requests are sent to if any member of\n");
    System.err.format( "                      the address-list (-a and EPICS_CA_ADDR_LIST) does not\n");
    System.err.format( "                      explicitly specify a port number. The default value is %d.\n\n", CaxyConst.CA_SERVER_PORT);
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
