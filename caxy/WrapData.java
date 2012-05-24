package caxy;

import java.nio.ByteBuffer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class WrapData {
	int 			  version;
	InetSocketAddress saddr, caddr;
	ByteBuffer        raw;
	
	/* size of raw data */
	static final int  size = 1 + 4 + 4 + 2 + 2;

	protected byte [] a2ba(int addr)
	{
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.putInt(addr);
		bb.flip();
		return bb.array();
	}

	protected InetAddress a2ina(int a)
	{
		InetAddress ina;

		try {
			ina = InetAddress.getByAddress(a2ba(a));
		} catch (UnknownHostException e)
		{
			ina = null;
			/* don't know what to do here */
		}
		return ina;
	}

	protected InetAddress a2ina(ByteBuffer b, int a)
	{
		int         cur = b.position();
		ByteBuffer  sl  = b.wrap(b.array(), cur, 4);
		InetAddress ina;
		
		b.putInt(a);

		System.out.println("B : " + b.toString());
		System.out.println("SL: " + sl.toString());

		try {
			byte [] ba = sl.array();
			CaxyStream.s.println(ba[0]+"."+ba[1]+"."+ba[2]+"."+ba[3]);
			ina = InetAddress.getByAddress(ba);
		} catch (UnknownHostException e)
		{
			CaxyStream.s.println("WrapData: UnknownHostException (slice pos: " + sl.position() + " len: " + sl.limit());
			ina = null;
			/* don't know what to do here */
		}
		System.out.println(ina.toString());
		return ina;
	}


	WrapData(int saddri, int sport, int caddri, int cport)
	{
		ByteBuffer  ba;
		int         pos;
		InetAddress ina;

		raw = ByteBuffer.allocate(size);

		raw.put( (byte)((version = CaxyConst.CATUN_VERSION_1) & 0xff) );

		ina = a2ina(raw, saddri);
		saddr = new InetSocketAddress(ina, sport);

		ina = a2ina(raw, caddri);
		caddr = new InetSocketAddress(ina, cport);

		raw.putShort((short)(sport & 0xffff));
		raw.putShort((short)(cport & 0xffff));
	}
	

	void dump()
	{
		CaxyStream.s.println("CATUN HDR");
		CaxyStream.s.format( "version       : 0x%02x\n", version);
		CaxyStream.s.format( "server address: %s\n", saddr == null ? "NULL" : saddr.toString());
		CaxyStream.s.format( "client address: %s\n", caddr == null ? "NULL" : caddr.toString());
		dumpraw();
	}

	synchronized void dumpraw()
	{
		int i,pos;
		pos = raw.position();
		raw.rewind();
		for ( i=0; i<raw.limit(); i++ ) {
			CaxyStream.s.format("0x%02x ", raw.get());
		}
		raw.position(pos);
	}

	public static void main(String [] args)
	{
		WrapData wd = new WrapData(0x01020304,4444,0x05060708,5555);
		wd.dump();
	}
}
