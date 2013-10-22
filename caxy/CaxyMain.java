package caxy;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.util.LinkedList;
import java.util.ListIterator;

import gnu.getopt.Getopt;

//class Getopt {
//	
//	private static java.lang.reflect.Constructor<?>  ctor = null;
//	private static java.lang.reflect.Method      m_getopt = null;
//	private static java.lang.reflect.Method      m_optArg = null;
//	private static java.lang.reflect.Method      m_optInd = null;
//	
//	private Object g_o;
//
//	public Getopt(String name, String []args, String opt) {
//		lazy_init();
//		try {
//			g_o = ctor.newInstance( new Object[] { name, args, opt } );
//		} catch (IllegalArgumentException e) {
//		} catch (InstantiationException e) {
//		} catch (IllegalAccessException e) {
//		} catch (InvocationTargetException e) {
//		} catch (NullPointerException e) {
//		}
//	}
//	
//	public int getopt() {
//		int rval = -1;
//		try {
//			rval = ((Integer)m_getopt.invoke(g_o, (Object[])null)).intValue();
//		} catch (IllegalArgumentException e) {
//		} catch (IllegalAccessException e) {
//		} catch (InvocationTargetException e) {
//		} catch (NullPointerException e) {
//		}
//		return rval;
//	}
//	
//	public int getOptind() {
//		int rval = 0;
//		try {
//			rval = ((Integer)m_optInd.invoke(g_o, (Object[])null)).intValue();
//		} catch (IllegalArgumentException e) {
//		} catch (IllegalAccessException e) {
//		} catch (InvocationTargetException e) {
//		} catch (NullPointerException e) {
//		}
//		return rval;
//	}
//	
//	public String getOptarg() {
//		String rval = null;
//		try {
//			rval = (String)m_optArg.invoke(g_o, (Object[])null);
//		} catch (IllegalArgumentException e) {
//		} catch (IllegalAccessException e) {
//		} catch (InvocationTargetException e) {
//		} catch (NullPointerException e) {
//		}
//		return rval;
//	}
//	
//	private static boolean init_done = false;
//	
//	private static synchronized void lazy_init() {
//		
//		if ( init_done )
//			return;
//		
//		String class_name = "gnu.getopt.Getopt";
//		
//		Exception e = null;
//		try {
//			Class<?> clazz = Class.forName( class_name );
//			ctor     = clazz.getConstructor( new Class<?>[] { String.class, String[].class, String.class });
//			m_getopt = clazz.getMethod("getopt",    (Class<?>[])null );
//			m_optArg = clazz.getMethod("getOptarg", (Class<?>[])null );
//			m_optInd = clazz.getMethod("getOptind", (Class<?>[])null );
//		} catch (ClassNotFoundException ex) {
//			e = ex;
//		} catch (NoSuchMethodException ex) {
//			e = ex;
//			ctor = null;
//		}
//		if ( null != e )
//			System.err.println(class_name + " class not found (" + e + "); option processing disabled!");
//		
//		init_done = true;
//	}
//}

class CaxyMain {

public static final String name = "caxyj";

public static void main(String [] args)
		throws FileNotFoundException, IOException
	{
	PktInpChannel         inpStrm;
	PktOutChannel         outStrm;
	TunnelHandler         tunlHdlr;
	int                   tunnel_port = TunnelHandler.TUNNEL_PORT_DFLT;
	String                env_addrlst = null;
	int                   server_port;
	int                   rpeatr_port;
	int                   debug  = 0;
	Getopt                g;
	int                   opt;
	boolean               inside = false;
	boolean               server = false;
	LinkedList<String>    alist  = new LinkedList<String>();
	CaxyJcaProp           props  = null;
	String                jcaPre = null;
	String                str;
	boolean               use_env, auto_alist = true, auto_alist_set = false;
	ServerSocketChannel   srvChn = null;
	boolean               local  = true;

		
		g = new Getopt(name, args, "a:A:d:hIJ:p:PVSf");
		
		while ( (opt = g.getopt()) > 0 ) {
			switch ( opt ) {
				case 'a':
					alist.add( g.getOptarg() );
				break;

				case 'A':
					auto_alist     = Boolean.valueOf( g.getOptarg() );
					auto_alist_set = true;
				break;
	
				case 'd':
					try {
						debug = Integer.decode( g.getOptarg() ).intValue();
					} catch ( java.lang.NumberFormatException e ) {
						System.err.println("Illegal argument to -d; must be numerical");
						System.exit(1);
					}
				break;

				case 'h':
					usage(name);
					System.exit(0);
				break;

				case 'P':
					local = false;
				break;

				case 'p':
					try {
						tunnel_port = Integer.decode(g.getOptarg()).intValue();
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

				case 'V':
					System.err.println(name+" release: " + CaxyVers.VERSION_STR);
					System.exit(0);
				break;

				case 'f':
					// ignored; server always runs in the foreground
				break;

				case 'S':
					server = true;
					inside = true;
				break;

				default:
					System.err.println("Unknown option '" + (char)opt + "': ignoring");
				break;
			}
		}

		if ( 0 == tunnel_port && server ) {
			System.err.println("Cannot work over STDIO in server mode; use -p to give me a port");
			System.exit(1);
		}

		try {
			props = new CaxyJcaProp( jcaPre, (CaxyConst.DEBUG_PROPS & debug) != 0 );
		} catch ( Exception e ) {
			System.err.println("Illegal argument to -P; unable to load properties");
			System.err.println(e);
			System.exit(1);
		} finally {
			jcaPre = null;
		}

		str = props.getProperty( "jca.use_env" );
		if ( null == str ) {
			str = props.getJcaProperty( "jca.use_env", "true" );
		}
		use_env = Boolean.valueOf( str );

		if ( (CaxyConst.DEBUG_PROPS & debug) != 0 )
			System.err.println("Using configuration values from " + (use_env ? "ENVIRONMENT" : "PROPERTIES"));

		if ( use_env ) {
			server_port = getIntEnv("EPICS_CA_SERVER_PORT",   CaxyConst.CA_SERVER_PORT);
			rpeatr_port = getIntEnv("EPICS_CA_REPEATER_PORT", CaxyConst.CA_REPEATER_PORT);
			if ( inside ) {
				env_addrlst = System.getenv("EPICS_CA_ADDR_LIST");
				if ( ! auto_alist_set ) { /* not enforced by commandline option */
					str = System.getenv("EPICS_CA_AUTO_ADDR_LIST");
					auto_alist = null == str || ! str.equalsIgnoreCase("NO");
					auto_alist_set = true;
				}
			}
		} else {
			server_port = getIntProp( props, "server_port",   CaxyConst.CA_SERVER_PORT );
			rpeatr_port = getIntProp( props, "repeater_port", CaxyConst.CA_REPEATER_PORT );

			if ( inside ) {
				env_addrlst = props.getJcaProperty( "addr_list" );
				if ( ! auto_alist_set ) { /* not enforced by commandline option */
					auto_alist = props.getJcaBoolProperty( "auto_addr_list", true );
					auto_alist_set = true;
				}
			}
		}

		props = null;

		TunnelHandlerEnv tunEnv = new TunnelHandlerEnv(inside, server_port, rpeatr_port, debug);

		if ( inside ) {
			ListIterator<String> i;
			if ( null != env_addrlst ) {
				alist.add( env_addrlst );
				env_addrlst           = null;
			}

			for ( i = alist.listIterator(); i.hasNext(); ) {
				tunEnv.addDstAddresses( i.next(), server_port );
			}

			if ( auto_alist ) {
				try {
					Class<?> autoAddrCls  = Class.forName("caxy.AutoAddr");
					Method   getBcstAddrs = autoAddrCls.getMethod("getList", new Class[] { Boolean.TYPE });
					tunEnv.addDstAddresses( 
							(InetAddress[])getBcstAddrs.invoke( null, new Object[] {  0 != (debug & CaxyConst.DEBUG_ALIST) } ),
							server_port );

				} catch (ClassNotFoundException e) {
					System.err.println("Unable to assemble CA auto address list: not supported under Java < 1.6 !");
				} catch (Throwable e) {
					System.err.println("Unable to assemble CA auto address list: ");
					System.err.println( e );
					e.printStackTrace();
					System.exit(1);
				}
			}
				
			if ( tunEnv.getLength() == 0 ) {
				System.err.format("Error: NO CA ADDRESS LIST in -I mode\n\n");
				System.err.format("Must set %s or use '-a' (using %s)\n\n",
				                  use_env ? "'EPICS_CA_ADDR_LIST' env-var" : "'gov.aps.jca.Context.addr_list' property",
				                  use_env ? "ENVIRONMENT" : "PROPERTIES");

				System.err.println("To use environment variables, make sure 'jca.use_env' property");
				System.err.println("is either true or undefined.");
				System.err.println();
				System.err.println("To use properties, the 'jca.use_env' property must be set to 'false'.");
				System.err.println();

				System.err.format("Use -d 0x%x to track property-related problems\n", CaxyConst.DEBUG_PROPS);
				System.exit(1);
			}

			if ( 0 != (debug & CaxyConst.DEBUG_ALIST) ) {
				tunEnv.dumpDstAddresses();
			}
		} else {
			tunEnv.addDstAddress("0.0.0.0",0);
		}

		alist = null;

		ClntProxyPool.initClass( debug );

		try {
			do {
				if ( 0 == tunnel_port ) {
					int l = args.length - g.getOptind(), i, j;
					if ( l > 0 && ! inside ) {
						Process   p;
						String [] cmd_args = new String[l];

						j = g.getOptind();
						for ( i=0; i<l; i++ )
							cmd_args[i] = args[j + i];

						p       = Runtime.getRuntime().exec(cmd_args);
						outStrm = new PktOutStrmChannel( p.getOutputStream() );
						inpStrm = new PktInpChannel(Channels.newChannel(p.getInputStream()));
						new Errlog(p.getErrorStream());
					} else {
						outStrm = PktOutChannel.getStdout();
						inpStrm = PktInpChannel.getStdin();
					}
				} else {
					PktBidChannel bid;
					try {
						if ( inside ) {
							if ( null == srvChn ) {
								srvChn = PktBidChannel.createSrvChannel( local, tunnel_port, 2 );
							}
							bid = new PktBidChannel( srvChn );
							if ( ! server ) {
								srvChn.close();
								srvChn = null;
							}
						} else {
							bid = new PktBidChannel( tunnel_port );
						}
					} catch (IOException e) {
						System.err.println("Unable to create TCP channel (on port " + tunnel_port + "): " + e);
						throw(e);
					}
					outStrm = bid.getPktOutChannel();
					inpStrm = bid.getPktInpChannel();
				}

				ClntProxyPool pool = new ClntProxyPool( outStrm );

				tunlHdlr = new TunnelHandler(pool, inpStrm, tunEnv);

				if ( server ) {
					(new Thread(tunlHdlr)).start();
				} else {
					tunlHdlr.execute();
				}

			} while ( server );

			if ( null != srvChn )
				srvChn.close();

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

	System.err.format( "Usage: %s [-h] [-S -I -P -a addr_list] [-d debug_flags] [-p tunnel_port] [-J prefix] [ [--] cmd [ args ] ]\n\n", nm);

	System.err.format( "  OPTIONS:\n\n");

    System.err.format( "       -I             Run in 'inside' mode as a proxy for CA clients\n");
    System.err.format( "                      on the 'outside'.\n\n");

	System.err.format( "       -S             'server-mode'. Like '-I' but manage/accept multiple connections\n");
	System.err.format( "                      from multiple 'outside' clients. The main program remains\n");
	System.err.format( "                      running in the foreground. Useful also if the 'outside' client\n");
	System.err.format( "                      dies and is restarted; no restart of the 'inside' is necessary\n");
	System.err.format( "                      in this mode. REQUIRES -p, i.e., a server cannot use STDIO for\n");
	System.err.format( "                      tunnel traffic.\n\n");

	System.err.format( "       -f             ignored for compatibility with C version. Note that -S always\n");
	System.err.format( "                      executes in the foreground.\n\n");

    System.err.format( "       -p tunnel_port TCP port to use for the tunnel (defaults to: %d).\n", TunnelHandler.TUNNEL_PORT_DFLT);
	System.err.format( "                      This flag is available on both, the 'inside' and the 'outside'.\n");
	System.err.format( "                      The setting MUST match the port numbers forwarded by SSH.\n");
	System.err.format( "                      E.g., if you use an explicit ssh tunnel\n\n");
	System.err.format( "                         ssh -L <outs_port>:localhost:<ins_port> ins_host\n\n");
	System.err.format( "                      then on the outside you must execute\n\n");
	System.err.format( "                         %s -p <outs_port>\n\n", nm);
	System.err.format( "                      and on the inside\n\n");
	System.err.format( "                         %s -I -p <ins_port>\n\n", nm);
	System.err.format( "                      OTOH, if you 'proxify' the tunnel also via ssh's -D\n");
	System.err.format( "                      option then the 'tunnel_port' on either side must be the same.\n\n");

	System.err.format( "                      NOTE: you may say '-p0' (a zero port number) in which case\n");
	System.err.format( "                            %s will use STDIO for tunnel traffic. You may want to use\n", nm);
	System.err.format( "                            the '-- cmd [args]' feature (see below) to set up a tunnel\n");
	System.err.format( "                            over SSH's stdio.\n\n");

	System.err.format( "       -P             Accept connections from anywhere (only relevant on 'inside' and when not\n");
	System.err.format( "                      using STDIO for tunnel traffic; see -p, -S). By default only connections\n");
	System.err.format( "                      from the machine where %s is running are accepted.\n\n", nm);

    System.err.format( "       -a addr_list   White-space separated list of 'addr[:port]' items. This option\n");
    System.err.format( "                      has the same effect as (and is augmented by) the env-var\n");
    System.err.format( "                      EPICS_CA_ADDR_LIST. Only effective if -I is given. CA search\n");
    System.err.format( "                      requests from the outside-client are forwarded to all members\n");
    System.err.format( "                      on the combined list on the 'inside' network.\n");
    System.err.format( "                      Multiple -a options may be given.\n\n");

    System.err.format( "       -A <boolval>   Enforce auto_addr_list; the value may be 'true' or 'false' (anything\n");
    System.err.format( "                      but 'true' is 'false'. This overrides value retrieved from environment\n");
    System.err.format( "                      ('EPICS_CA_AUTO_ADDR_LIST') or properties ('auto_addr_list').\n\n");

    System.err.format( "       -d debug_flags Enable debug messages (on stderr). 'debug_flags' is a bitset\n");
    System.err.format( "                      of switches:\n");
    System.err.format( "                              0x%x: dump incoming UDP frames\n",       CaxyConst.DEBUG_UDP);
    System.err.format( "                              0x%x: dump incoming TCP frames\n",       CaxyConst.DEBUG_TCP);
    System.err.format( "                              0x%x: omit CA beacon messages\n",        CaxyConst.DEBUG_NOB);
    System.err.format( "                          0x%x: trace how properties are looked up\n", CaxyConst.DEBUG_PROPS);
    System.err.format( "                          0x%x: trace how CA addr list is assembled\n",CaxyConst.DEBUG_ALIST);

	System.err.println();

//	if ( false ) {
//	/* not supported (yet) */
//    System.err.format( "       -n             Do not do any DNS lookup when dumping IP addresses.\n");
//    System.err.format( "                      Has only an effect on info printed by -d. Use this\n");
//    System.err.format( "                      option if the program executes very slowly due to DNS\n");
//    System.err.format( "                      problems or general slow-ness.\n\n");
//	}

	System.err.format( "       -J             Prefix string when looking up 'JCA' context properties; e.g.,\n");
	System.err.format( "                      'gov.aps.jca.jni.JNIContext' or 'com.cosylab.epics.caj.CAJContext'.\n");
	System.err.format( "                      If no prefix is set or a resource with the given prefix is not found\n");
	System.err.format( "                      then a an attempt using the 'default' prefix 'gov.aps.jca.Context'\n");
	System.err.format( "                      is made.\n\n");

    System.err.format( "       -h             Print this information.\n\n");

    System.err.format( "       -V             Print release information\n\n");

    System.err.format( "       --             STRONGLY RECOMMENDED if <cmd> [<args>] is used. Marks\n");
    System.err.format( "                      the end for %s's option processing. This prevents any\n", nm);
    System.err.format( "                      options given to <cmd> being 'eaten' by caxy\n\n");

    System.err.format( "       <cmd> [<args>] If any extra parameters are given to %s (outside mode\n", nm);
    System.err.format( "                      only) then a new process is spawned, trying to execute <cmd>.\n");
    System.err.format( "                      All subsequent <args> are passed on to this process.\n");
    System.err.format( "                      Most importantly, caxy's stdin/stdout streams are connected\n");
    System.err.format( "                      to the process' stdout/stdin, respectively. The process'\n");
    System.err.format( "                      stderr stream is copied (by a dedicated thread) verbatim to\n");
    System.err.format( "                      caxy's stderr.\n");
    System.err.format( "                      This feature is extremely useful to set up a tunnel. <cmd>\n");
    System.err.format( "                      typically launches an SSH session executing an 'inside' version\n");
    System.err.format( "                      of caxy on the 'inside'. Here is an example:\n\n");
    System.err.format( "                        java -jar caxy.jar -- ssh -D 1080 user@insidehost java -jar caxy.jar -I\n\n");

	System.err.println();

	System.err.format( "  ENVIRONMENT:\n\n");

	System.err.format( "       NOTE: Environment variables are ONLY read if the system property 'jca.use_env' is\n");
	System.err.format( "             set to 'true' or unset and if the JCA property 'use_env' (using one of the JCA\n");
	System.err.format( "             prefixes, see '-J' above) is either not set or set to 'true'\n");
	System.err.format( "             If 'use_env' is determined to be 'false' then environment variables are ignored\n");
	System.err.format( "             and JCA properties 'server_port', 'repeater_port', 'addr_list' and 'auto_addr_list'\n");
	System.err.format( "             are looked up, respectively.\n");
	System.err.format( "             JCA properties have the prefix as given (in order of precedence) by\n");
	System.err.format( "             '-J <prefix>', or the string 'gov.aps.jca.Context' as a fallback.\n");
	System.err.format( "             Properties are first looked up in the user properties (path itself defined by\n");
	System.err.format( "             JCA property 'gov.aps.jca.JCALibrary.properties' or if such a property is not\n");
	System.err.format( "             found then '.JCALibrary/JCALibrary.properties' in the user's home directory\n");
	System.err.format( "             is used). If no user-specific property is found then the system-wide ones\n");
	System.err.format( "             (located in 'lib/JCALibrary.properties' in the 'java.home' directory) are consulted\n");
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
	System.err.format( "                      for EPICS_CA_REPEATER_PORT on the inside and outside.\n\n");

    System.err.format( "       EPICS_CA_ADDR_LIST (unused in 'outside' mode)\n");
    System.err.format( "                      White-space separated list of <address>[:<port>] items defining\n");
    System.err.format( "                      all addresses where the 'inside' proxy should send CA search\n");
    System.err.format( "                      requests. Consult EPICS documentation for more details.\n");
    System.err.format( "                      Note that <address> may be a DNS name or plain IP address.\n");
    System.err.format( "                      If no port number is given then the ('inside') value\n");
    System.err.format( "                      of EPICS_CA_SERVER_PORT is used. The contents of this variable\n");
    System.err.format( "                      are appended to all '-a' options.\n\n");

    System.err.format( "       EPICS_CA_AUTO_ADDR_LIST (unused in 'outside' mode)\n");
    System.err.format( "                      If unset or set to anything but 'NO' then a list of all broadcast\n");
    System.err.format( "                      addresses of all interfaces of the host is computed and appended\n");
    System.err.format( "                      to any addresses present in EPICS_CA_ADDR_LIST and '-a' options.\n\n");
    System.err.format( "                        THIS FEATURE IS NOT AVAILABLE UNDER JAVA < 1.6\n\n");
    System.err.format( "                      Usage of an 'auto_addr_list' can also be forced on or off by means\n");
    System.err.format( "                      of a '-A true' or '-A false' command line option which takes\n");
    System.err.format( "                      precedence if present.\n\n");
	}
	
	public static int getIntEnv(String env_var, int defltVal)
	{
	String str;
		if ( (str = System.getenv(env_var)) != null ) {
			try {
				return Integer.decode(str).intValue();
			} catch ( java.lang.NumberFormatException e ) {
				System.err.println("Unable to parse "+env_var+" env-var");
				System.exit(1);
			}
		}
		return defltVal;
	}

	public static int getIntProp(CaxyJcaProp props, String key, int defltVal)
	{
		try {
			return props.getJcaIntProperty(key, defltVal);
		} catch ( CaxyJcaProp.JCAPropertyFormatException e ) {
			e.printStackTrace();
			System.exit( 1 );
		}
		return defltVal;
	}
}
