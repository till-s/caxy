package caxy;

import java.nio.ByteBuffer;
import java.net.SocketAddress;
import java.io.IOException;

class WrapHdr {
	protected ByteBuffer b;

	protected static final int WRAP_VERS_OFF  = 0;
	protected static final int WRAP_N_CA_OFF  = 2;
	protected static final int WRAP_SADDR_OFF = 4;
	protected static final int WRAP_CADDR_OFF = 8;
	protected static final int WRAP_SPORT_OFF = 12;
	protected static final int WRAP_CPORT_OFF = 14;

	public int get_n_ca()  { b.position(WRAP_N_CA_OFF);  return (b.getShort()) & 0xffff; }
	public int get_saddr() { b.position(WRAP_SADDR_OFF); return b.getInt(); }
	public int get_caddr() { b.position(WRAP_CADDR_OFF); return b.getInt(); }
	public int get_sport() { b.position(WRAP_SPORT_OFF); return (b.getShort() & 0xffff); }
	public int get_cport() { b.position(WRAP_CPORT_OFF); return (b.getShort() & 0xffff); }

	public WrapHdr()
	{ b = ByteBuffer.allocate(2+2+4+4+2+2); }

	public ByteBuffer fill(int n_ca, int saddr, int caddr, int sport, int cport)
	{
		b.clear();
		b.put((byte)CaxyConst.CATUN_VERSION_2);
		b.position(WRAP_N_CA_OFF);
		b.putShort((short)(n_ca & 0xffff));
		b.putInt(saddr);
		b.putInt(caddr);
		b.putShort((short)(sport & 0xffff));
		b.putShort((short)(cport & 0xffff));
		b.flip();
		return b;
	}

	public void out(PktOutChannel chnl, SocketAddress peer)
		throws IOException, PktOutChannel.IncompleteBufferWrittenException
	{
		b.rewind();
		chnl.putBuf(b, peer);
	}

	public void read(PktInpChannel chnl)
		throws IOException, CaxyBadVersionException, PktInpChannel.IncompleteBufferReadException
	{
	int version;

		b.clear();
		chnl.getPkt(b);
		b.flip();

		version = b.get();
		if ( CaxyConst.CATUN_VERSION_2 != (CaxyConst.CATUN_MAJOR_MSK & version) )
			throw new CaxyBadVersionException(version);
	}

	public void dump(int debug)
	{
	int opos = b.position();
		b.rewind();
		System.err.println("CATUN Hdr:");
		System.err.format( "version: 0x%02x\n", b.get());
		b.position(WRAP_N_CA_OFF);
		System.err.format( "N CAMSG: %d\n", b.getShort() & 0xffff);
		System.err.format( "Server : %d.%d.%d.%d\n", b.get() & 0xff, b.get() & 0xff, b.get() & 0xff, b.get() & 0xff);
		System.err.format( "Client : %d.%d.%d.%d\n", b.get() & 0xff, b.get() & 0xff, b.get() & 0xff, b.get() & 0xff);
		System.err.format( "Srv Prt: %d\n", (b.getShort()&0xffff));
		System.err.format( "Clt Prt: %d\n", (b.getShort()&0xffff));

		if ( (debug & CaxyConst.DEBUG_RAWBUF) != 0 ) {
			byte v;

			b.rewind();
			System.err.println("Raw Contents:");
			while ( b.remaining() > 0 ) {
				v = b.get();
				System.err.format( "0x%02x '%c' ", v, (char)v );
			} 
			System.err.println();
		}

		b.position(opos);
	}

	class CaxyBadVersionException extends Exception {
		public final int badVersion;
		protected CaxyBadVersionException(int version)
		{
			super( "Bad CATUN protocol version: " + version );
			badVersion = version;
		}
	}
}
