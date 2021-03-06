/* 
 * Authorship
 * ----------
 * This file  was created by
 *
 *    Till Straumann <strauman@slac.stanford.edu>, 2011
 * 	  SLAC National Laboratory, Stanford University.
 *
 * Acknowledgement of sponsorship
 * ------------------------------
 * This software was produced by
 *     SLAC, Stanford University,
 * 	   under Contract DE-AC03-76SFO0515 with the Department of Energy.
 * 
 * Government disclaimer of liability
 * ----------------------------------
 * Neither the United States nor the United States Department of Energy,
 * nor any of their employees, makes any warranty, express or implied, or
 * assumes any legal liability or responsibility for the accuracy,
 * completeness, or usefulness of any data, apparatus, product, or process
 * disclosed, or represents that its use would not infringe privately owned
 * rights.
 * 
 * Stanford disclaimer of liability
 * --------------------------------
 * Stanford University makes no representations or warranties, express or
 * implied, nor assumes any liability for the use of this software.
 * 
 * Stanford disclaimer of copyright
 * --------------------------------
 * Stanford University, owner of the copyright, hereby disclaims its
 * copyright and all other rights in this software.  Hence, anyone may
 * freely use it for any purpose without restriction.  
 * 
 * Maintenance of notices
 * ----------------------
 * In the interest of clarity regarding the origin and status of this
 * SLAC software, this and all the preceding Stanford University notices
 * are to remain affixed to any copy or derivative of this software made
 * or distributed by the recipient and are to be affixed to any copy of
 * software made or distributed by the recipient that contains a copy or
 * derivative of this software.
 * 
 * ------------------ SLAC Software Notices, Set 4 OTT.002a, 2004 FEB 03
 */ 

package caxy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;

/*
 * ProxifiedSocketChannel
 *
 * implements establishing a connection to a remote host via a SOCKS4/SOCKS4a or
 * SOCKS5 proxy.
 *
 * Note that the 'Socket' class supports SOCKS (and other proxies) transparently,
 * i.e., out of the box. Unfortunately, the designers of SocketChannel did not
 * build proxy support into that class. In particular, the Socket member of 
 * SocketChannel does *not* support proxies either (and it is not an 'ordinary'
 * Socket in many other respects -- e.g., timeouts -- either).
 * I.e., 
 *
 *    SocketChannel sc; ... sc.socket().connect( addr );
 *
 * *ignores* any proxies (unlike Socket s; ... s.connect(addr); ).
 *
 * When opening a SocketChannel via ProxifiedSocketChannel.open(SocketAddress) then
 * the installed ProxySelector is queried for a proxy to use for destination
 * 'SocketAddress' and if a proxy is found then a SocketChannel to the proxy is
 * created and a proxied connection is negotiated with the proxy server.
 * Upon success, the proxied SocketChannel is returned to the caller.
 *
 * Note that java.net.Proxy does not store a username/password for the proxy.
 * Therefore, username/password authentication is currently *not* supported by
 * this ProxifiedSocketChannel.
 *
 * Eclipse's ProxySelector and Proxy classes are more complete than the
 * versions in java.net (but are not derived classes but entirely different).
 * Since we don't want to depend on eclipse here we stick to java.net.
 *
 * As of eclipse 3.6 the proxy settings established by eclipse's GUI
 * (Preferences/General/Network Connections) are completely orthogonal
 * to java.net. It is, however, possible (and recommended) that -- until
 * Eclipse installs a proper java.net.ProxySelector -- an appropriate
 * plugin such as org.eclipse.scout.net is used which takes care of
 * installing a java.net.ProxySelector so that eclipse's settings
 * are picket up by java.net.ProxySelector (used by this class).
 * But again - even though eclipse allows the user to specify a 
 * proxy-specific username/password this information is not propagated
 * to java.net.ProxySelector. Hence, ProxifiedSocketChannel does *NOT*
 * support any other authentication method than 'NONE'. We feel that
 * this is acceptable as in most cases the proxy is SSH on localhost.
 * 
 * Another note regarding ProxySelector: the default selector 
 * (sun.net.spi.DefaultProxySelector) is quite limited as it only
 * allows for the definition of a single, system-wide proxy which
 * is used for ALL connections. Note that unlike for http and ftp
 * there is NO support for a 'socks.nonProxyHosts' system-property
 * list, i.e., the default selector does not allow the user to
 * enforce direct connections to some addresses.
 *
 * If you need anything more sophisticated than this default
 * (all connections go through one and the same or no proxy
 * at all) then you need to implement your own ProxySelector.
 * As mentioned above - eclipses' selector is a bit more elaborate
 * (you have ONE proxy and a list of exceptions).
 *
 */

/* A list of proxies for a given destination address (specified in the
 * constructor). 
 * A list iterator filters this list for SOCKS proxies.
 */
class ProxyList {

	protected Iterator<Proxy> proxyListIterator;

	protected ProxyList(SocketAddress remote)
	{
		// obtain default ProxySelector -- sun.net.spi.DefaultProxySelector
		// is quite limited!
		ProxySelector     psel   = ProxySelector.getDefault();
		URI               dstUri = null;
		List<Proxy>       plst   = null;
		InetSocketAddress isa    = (InetSocketAddress)remote;

			// build an URI from a SocketAddress -- surprisingly this seems to be quite
			// difficult as SocketAddress.toString() does not yield (under all circumstances)
			// what we want.
		dstUri = URI.create("socket://"
			                 + (isa.isUnresolved() ? isa.getHostName()
                                                   : isa.getAddress().getHostAddress())
			                 + ":" + isa.getPort());
/*
		catch ( java.net.URISyntaxException e ) {
			System.out.println("ProxifiedSocketChannel.findProxy() -- " + e);
			System.out.println("Using wildcard destination");
			dstUri = URI.create("socket://:");
		}
*/

		if ( ProxifiedSocketChannel.debug )
			System.out.println("Find proxy for URI: " + dstUri);
		plst = psel.select( dstUri );
		proxyListIterator = (null != plst ? plst.iterator() : null);
	}

	// Return next SOCKS proxy on the list or NULL if none found
	protected Proxy nextSOCKSProxy()
	{
		Proxy p = null;

		if ( null == proxyListIterator )
			return null;

		while ( proxyListIterator.hasNext() ) {
			p = (Proxy)proxyListIterator.next();

			if ( ProxifiedSocketChannel.debug ) 
				System.out.println("Checking next proxy " + p);

			if ( Type.SOCKS == p.type() && null != p.address() )
				return p;
		}

		return null;
	}
}


public class ProxifiedSocketChannel {

	// Constants defining the SOCKS protocols V4 and V5

	static final byte SOCKS4_VERS                 = 0x04;
	static final byte SOCKS5_VERS                 = 0x05;

	static final byte SOCKS4_CMD_CONN             = 0x01;
	static final byte SOCKS4_CMD_BIND             = 0x02;

	static final byte SOCKS4_STA_GRANTED          = 0x5a;
	static final byte SOCKS4_STA_FAILED           = 0x5b;
	static final byte SOCKS4_STA_FAILED_NO_IDENTD = 0x5c;
	static final byte SOCKS4_STA_FAILED_IDENTD    = 0x5d;

	// length of request not including username
	static final byte SOCKS4_REQ_LEN              =    8;
	static final byte SOCKS4_REP_LEN              =    8;

	static final byte SOCKS5_AUTH_NONE            = 0x00;
	static final byte SOCKS5_AUTH_GSSAPI          = 0x01;
	static final byte SOCKS5_AUTH_USRNAME         = 0x02;

	static final byte SOCKS5_CMD_CONN             = 0x01;
	static final byte SOCKS5_CMD_BIND             = 0x02;
	static final byte SOCKS5_CMD_ASSOC            = 0x03;

	static final byte SOCKS5_ADDR_IPV4            = 0x01;
	static final byte SOCKS5_ADDR_DNS             = 0x03;
	static final byte SOCKS5_ADDR_IPV6            = 0x04;

	static final byte SOCKS5_REQ_LEN              =    6;
	static final byte SOCKS5_REP_LEN              =   20; // not covering a DNS reply length

	static final byte SOCKS5_STA_GRANTED          = 0x00;
	static final byte SOCKS5_STA_FAILED           = 0x01;
	static final byte SOCKS5_STA_RULEVIOLATION    = 0x02;
	static final byte SOCKS5_STA_NETUNREACH       = 0x03;
	static final byte SOCKS5_STA_HOSTUNREACH      = 0x04;
	static final byte SOCKS5_STA_CONNREFUSED      = 0x05;
	static final byte SOCKS5_STA_TTLEXPIRED       = 0x06;
	static final byte SOCKS5_STA_PROTOERR         = 0x07;
	static final byte SOCKS5_STA_ATYPEUNSUPP      = 0x08;

	static final byte SOCKS_NULL                  = 0x00;

	static final int  SOCKS_PROXY_TIMEOUT_MS      = 3000;

	static final boolean debug                    =
		System.getProperties().containsKey( "CAJ_DEBUG" );

	// SocketChannel does not directly support timeouts :-( so we must
	// implement some kludge. The reason is that we want to be able
	// to time-out when waiting for a reply from a SOCKS server (i.e.,
	// if there is no SOCKS server at the other end but somebody who
	// doesn't speak SOCKS then we never may get a reply and we don't
	// want to hang in such a case).
	// 
	// We set the channel to non-blocking mode and select() for a READ
	// operation with a timeout.
	// If the timeout expires then this routine throws a SocketException,
	// otherwise it switches back to blocking mode and returns normally.
	//
	// Hence, we don't time-out if the server sends an incomplete answer
	// back - just if it doesn't reply at all.
	protected static void timeoutReady( SocketChannel ch, int timeout_ms, int mask )
	throws IOException
	{
	Selector     sel = null;
	SelectionKey key = null;

		// Create a new selector
		try {
			sel = Selector.open();
	
			// Must switch to non-blocking mode in order to 'select'
			ch.configureBlocking( false );

			// register with selector
			key = ch.register(sel, mask);

			// wait for data to come in or time-out
			if ( 0 == sel.select( timeout_ms ) ) {
				throw new SocketException("No reply from SOCKS server");
			}

		} finally {
			// cancel our registration
			if ( null != key )
				key.cancel();
			if ( null != sel )
				sel.close();
		}

		// and switch back to blocking mode
		ch.configureBlocking( true );
	}

	protected static void timeoutReady4Read(SocketChannel ch, int timeout_ms)
	throws IOException
	{
		timeoutReady( ch, timeout_ms, SelectionKey.OP_READ );
	}

	protected static SocketChannel createChannel(InetSocketAddress remote)
	throws IOException
	{
	SocketChannel  ch = SocketChannel.open();
		try {
			ch.configureBlocking( false );
			ch.connect( remote );
			// timeoutReady() re-configures for blocking I/O upon success.
			timeoutReady( ch, SOCKS_PROXY_TIMEOUT_MS, SelectionKey.OP_CONNECT );
			ch.finishConnect();
		} catch (IOException e) {
			ch.close();
			ch = null;
			throw e;
		} catch (RuntimeException e) {
			ch.close();
			ch = null;
			throw e;
		}

		return ch;
	}


	// Negotiate a proxied connection to 'remote' over channel 'ch' using the SOCKS5
	// protocol. 'ch' is already connected to 'proxy'. The 'proxy' argument is supplied
	// only so that extra information (such as username/passwd) could be retrieved
	// (but username/passwd are not currently supported by java.net.Proxy).
	protected static boolean socks5Negotiate( SocketChannel ch, InetSocketAddress remote, Proxy prxy )
	throws IOException, SocketException
	{
	ByteBuffer   req = null;
	ByteBuffer   rep = null;
	int          len = SOCKS5_REQ_LEN;
	byte[]       hn  = null;
	byte[]       ha  = null;
	byte         addr;

		// FIXME: retrieve username to use for this proxy. Proxy class currently doesn't
		//        support this :-(

		// Find out what type of address 'remote' is. It could be
		// a resolved IPV4 or IPV6 address or an unresolved name.
		// SOCKS5 allows for any of these.
		if ( remote.isUnresolved() ) {
			// Convert host name to a (unicode-encoded) byte string
			hn   = remote.getHostName().getBytes();
			// Compute length of name + extra byte to store the length
			len += java.lang.reflect.Array.getLength(hn) + 1;
			addr = SOCKS5_ADDR_DNS;
		} else {
			ha   = remote.getAddress().getAddress();
			len += ha.length;
			// deduce the address-type (IPV4 vs IPV6) from the
			// length of the raw address.
			if ( 4 == ha.length )
				addr = SOCKS5_ADDR_IPV4;
			else
				addr = SOCKS5_ADDR_IPV6;
		}

		// Allocate a byte buffer that can hold everything
		req = ByteBuffer.allocate( len );

		// But first, we must send a greeting with the authentication
		// methods we support.
		req.put( SOCKS5_VERS );
		// just support no-auth ATM
		req.put( (byte)    1 );
		req.put( SOCKS5_AUTH_NONE );

		// flip buffer and send-off
		req.flip();
		ch.write( req );

		// wait for a reply or time-out; a time-out will throw an exception
		timeoutReady4Read( ch, SOCKS_PROXY_TIMEOUT_MS );

		// allocate a buffer for the reply -- this is only big enough
		// for a IPV6 address at most. If we have a long unresolved name
		// then we reallocate the buffer later.
		rep = ByteBuffer.allocate( SOCKS5_REP_LEN );
		// For now we just want the reply to the greeting and
		// find out which auth-method the server accepts.
		rep.limit( 2 );

		if ( ch.read( rep ) < 0 ) {
			throw new SocketException("SOCKS5 server closed/rejected greeting"); 
		}

		rep.flip();
		// verify the reply we got
		if ( SOCKS5_VERS != rep.get() || SOCKS5_AUTH_NONE != rep.get() ) {
			throw new SocketException("SOCKS5 unable to authenticate");
		}

		// everything is OK, so far.

		// now build the 'real' request but first, rewind the buffer.
		req.rewind();
		req.limit( req.capacity() );

		req.put( SOCKS5_VERS );
		// connection request
		req.put( SOCKS5_CMD_CONN );
		req.put( SOCKS_NULL );
		// address type
		req.put( addr );
		// and address data
		if ( remote.isUnresolved() ) {
			req.put( (byte)hn.length );
			req.put( hn );
		} else {
			req.put( ha );
		}
		// remote port. ByteBuffer -- by default -- stores in big-endian
		// AKA network-byte-order which is what we want.
		req.putShort( (short)remote.getPort() );

		req.flip();
		ch.write( req );

		// wait for a reply or time-out (which would throw an SocketException).
		timeoutReady4Read( ch, SOCKS_PROXY_TIMEOUT_MS );

		rep.rewind();
		// At first, just read the header.
		rep.limit(4);

		if ( ch.read( rep ) < 0 ) {
			throw new SocketException("SOCKS5 server closed/rejected request"); 
		}

		rep.flip();

		// Verify header
		if ( SOCKS5_VERS != rep.get() ) {
			throw new SocketException("SOCKS5 server replied version != 5");
		}

		switch ( rep.get() ) {
			case SOCKS5_STA_GRANTED          :
			break;

			case SOCKS5_STA_FAILED           :
				throw new SocketException("SOCKS5 general failure");
				
			case SOCKS5_STA_RULEVIOLATION    :
				throw new SocketException("SOCKS5 connection not allowed by ruleset");
			case SOCKS5_STA_NETUNREACH       :
				throw new SocketException("SOCKS5 network unreachable");
			case SOCKS5_STA_HOSTUNREACH      :
				throw new SocketException("SOCKS5 host unreachable");
			case SOCKS5_STA_CONNREFUSED      :
				throw new SocketException("SOCKS5 connection refused by destination host");
			case SOCKS5_STA_TTLEXPIRED       :
				throw new SocketException("SOCKS5 TTL expired");
			case SOCKS5_STA_PROTOERR         :
				throw new SocketException("SOCKS5 command not supported / protocol error");
			case SOCKS5_STA_ATYPEUNSUPP      :
				throw new SocketException("SOCKS5 address type not supported");
			default:
				throw new SocketException("SOCKS5 unknown status error");
		}

		if ( 0 != rep.get() ) {
				throw new SocketException("SOCKS5 malformed reply -- null byte expected");
		}

		// last byte in the header is the address type. From this we
		// compute how many more bytes we must read (just to clear the
		// channel -- we are not really interested in the data we get).

		len = 2; // space for port number

		switch ( (addr = rep.get()) ) {
			default:
				throw new SocketException("SOCKS5 malformed reply -- unknown address type");

			case SOCKS5_ADDR_IPV4: len +=  4; break;
			case SOCKS5_ADDR_IPV6: len += 16; break;
			case SOCKS5_ADDR_DNS:
				// read the length of the unresolved name
				rep.rewind();
				rep.limit(1);
				if ( ch.read(rep) < 0 ) {
					throw new SocketException("SOCKS5 server closed/rejected reading name length"); 
				}
				len += rep.get();
				// if the name doesn't fit in our buffer then
				// we have to allocate a bigger one.
				if ( rep.capacity() < len )
					rep = ByteBuffer.allocate( len );
			break;
		}

		// now slurp the address information (and discard)
		rep.rewind();
		rep.limit(len);
		if ( ch.read(rep) < 0 ) {
			throw new SocketException("SOCKS5 server closed/rejected reading address"); 
		}

		// worked out OK
		return true;
	}

	// Negotiate a proxied connection to 'remote' over channel 'ch' using the SOCKS4/SOCKS4a
	// protocol. 'ch' is already connected to 'proxy'. The 'proxy' argument is supplied
	// only so that extra information (such as username/passwd) could be retrieved
	// (but username/passwd are not currently supported by java.net.Proxy).
	protected static boolean socks4Negotiate( SocketChannel ch, InetSocketAddress remote, Proxy prxy )
	throws IOException, SocketException
	{
	ByteBuffer   req = null;
	ByteBuffer   rep = null;
	int          len = SOCKS4_REQ_LEN + 1 /* NULL username terminator */;
	byte[]       hn  = null;
	byte[]       ha  = null;
	byte         sta;

		// FIXME: retrieve username to use for this proxy. Proxy class currently doesn't
		//        support this :-(

		if ( remote.isUnresolved() ) {
			// If the remote address is unresolved then we use SOCKS4a
			// reserve space for the name
			hn   = remote.getHostName().getBytes();
			len += java.lang.reflect.Array.getLength(hn) + 1; // terminating NULL 
		}

		req = ByteBuffer.allocate( len );

		// build request
		req.put( SOCKS4_VERS     );
		req.put( SOCKS4_CMD_CONN );
		// ByteBuffer by default writes bytes in big-endian AKA
		// network-byte-order which is what we want.
		req.putShort( (short)remote.getPort() );

		if ( remote.isUnresolved() ) {
			// SOCKS4a says that we write an invalid IPV4 address
			// here (first three bytes zeroes, fourth byte non-zero)
			// and append the unresolved name to the request
			// packet.
			req.putInt( 0x00000003 );
		} else {
			ha = remote.getAddress().getAddress();
			if ( ha.length != 4 ) {
				throw new SocketException("SOCKS4 doesn't support IPV6 addresses");
			}
			req.put( ha );
		}

		req.put( SOCKS_NULL );

		// If we have an unresolved remote address then use SOCKS4a - append
		// unresolved name...

		if ( null != hn ) {
			req.put( hn );
			req.put( SOCKS_NULL );
		}

		req.flip();

		ch.write( req );


		// allocate buffer for the reply
		rep = ByteBuffer.allocate( SOCKS4_REP_LEN );


		// wait for a reply or time-out (which would throw an SocketException).
		timeoutReady4Read( ch, SOCKS_PROXY_TIMEOUT_MS );

		if ( ch.read( rep ) < 0 )
			throw new SocketException("SOCKS4 server closed/rejected connection");

		rep.flip();

		// verify the reply
		if ( 0x00 != rep.get() ) {
			throw new SocketException("Unexpected SOCKS4 reply byte 0 nonzero");
		}

		switch ( (sta = rep.get()) ) {
			case SOCKS4_STA_GRANTED          :
			break;

			case SOCKS4_STA_FAILED           :
				throw new SocketException("SOCKS4 Server denied CONNECT request");
			case SOCKS4_STA_FAILED_NO_IDENTD :
				throw new SocketException("SOCKS4 no identd");
			case SOCKS4_STA_FAILED_IDENTD    :
				throw new SocketException("SOCKS4 identd failure");
			default:
				throw new SocketException("SOCKS4 Server UNKNOWN status" + sta);
		}

		// seems to have worked out :-)
		return true;
	}

	// Open a socket channel to 'remote' via a proxy if the system ProxySelector
	// yields one for the 'remote' address. Otherwise open a direct connection.
	public static SocketChannel open(SocketAddress remote)
		throws IOException
	{
		SocketChannel     rval = null; 
		SocketChannel     sc   = null;
		Proxy             prxy = null;
		InetSocketAddress isa  = null;
		// construct a list of proxies
		ProxyList         plst = new ProxyList( remote );

		// scan the list for SOCKS proxies and try to use the
		// first one that works.
		while ( null != ( prxy = plst.nextSOCKSProxy() ) ) {
			// found a proxy
			try {
				if ( debug )
					System.out.println("Trying Proxy: " + prxy.address());

				isa = (InetSocketAddress)prxy.address();

				// a hack; I found that I get a UnresolvedAddressException
				// when using prxy.address() directly. Hence, we build a
				// new InetSocketAddress which will try a lookup
				if ( isa.isUnresolved() ) {
					isa = new InetSocketAddress( isa.getHostName(), isa.getPort() );
				}
				// try to negotiate a connection to 'remote' via 'prxy'; First try SOCKS5
				// and if that doesn't work then fall back to SOCKS4.
				try {
					// open a channel to the proxy
					sc = createChannel( isa );
					socks5Negotiate( sc, (InetSocketAddress)remote, prxy );
				} catch ( SocketException e )
				{

					// if we couldn't even connect (ConnectException is a subclass
					// of SocketException) then don't bother trying SOCKS4
					if ( e.getClass() == new java.net.ConnectException().getClass() )
						throw e;

					if ( debug ) {
						System.out.println("Proxy: " + isa + " : " + e);
						System.out.println("Falling back to SOCKS4");
					}

					// open a new channel to the proxy
					if ( null != sc )
						sc.close();
					sc = createChannel( isa );
					socks4Negotiate( sc, (InetSocketAddress)remote, prxy );
				}
// SUCCESS
				if ( debug )
					System.out.println("SOCKS Proxy Connection SUCCESS");

				rval = sc;
				sc   = null;
				return rval;
			} catch ( java.net.ConnectException e ) {
				System.out.println("Proxy: " + prxy.address() + " refused connection" );
				throw e;
			} catch ( java.nio.channels.UnresolvedAddressException e ) {
				System.out.println("Proxy: " + prxy.address() + " could not be resolved");
				throw e;
			} catch ( IOException e ) {
				// This also catches SocketException
				System.out.println("Proxy: " + prxy.address() + " " + e );
				throw e;
			} catch ( RuntimeException e ) {
				System.out.println("Proxy: " + prxy.address() + " " + e );
				throw e;
			} finally {
				if ( null != sc ) {
					System.out.println("Hmm - SOCKS Proxy Connection FAILED");
					sc.close();
					sc = null;
				}
			}
		}

		// try direct connection

		return createChannel( (InetSocketAddress)remote );
	}

	public static void main(String[] args)
		throws IOException
	{
	String        hn = "localhost";
	int           pt = 5555;
	ByteBuffer    bb = null;
	String        s  = "hello\n";
	SocketAddress sa = null;
	SocketChannel c  = null;

		if ( args.length > 0 )
			hn = args[0];
		if ( args.length > 1 )
			pt = Integer.parseInt( args[1] );

		sa = new InetSocketAddress(hn, pt);
		c  = ProxifiedSocketChannel.open(sa);
		bb = ByteBuffer.wrap(s.getBytes());
		c.write(bb);
	}
}
