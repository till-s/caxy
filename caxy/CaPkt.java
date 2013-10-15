package caxy;

import java.nio.ByteBuffer;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.io.IOException;

class CaPkt {

	public static final int  CA_PROTO_SEARCH      = 6;
	public static final int  CA_PROTO_RSRV_IS_UP  = 13;
	public static final int  REPEATER_CONFIRM     = 17;
	public static final int  REPEATER_REGISTER    = 24;
	public static final int  CA_MINOR_PROTO_REV_8 = 8;

	public static final int  CA_HDR_SIZE          = 16;

	public static final int M_CMMD_OFF            = 0;
	public static final int M_POSTSIZE_OFF        = 2;
	public static final int M_DATATYPE_OFF        = 4;
	public static final int M_COUNT_OFF           = 6;
	public static final int M_CID_OFF             = 8;
	public static final int M_AVAILABLE_OFF       = 12;

	protected ByteBuffer b;

	public CaPkt()
	{
		b = ByteBuffer.allocate( CA_HDR_SIZE );
		b.clear();
		while ( b.hasRemaining() )
			b.put((byte)0);
		b.clear();
	}

	public CaPkt(ByteBuffer b)
	{
		this.b = b;
	}

	/* b_in already contains a CA packet */
	public static CaPkt get(ByteBuffer b_in)
	{
		int l;

		CaPkt  p = new CaPkt( b_in.slice() );
		l  = p.get_m_postsize() + CA_HDR_SIZE;
		p.b.limit(l);
		p.b.rewind();

		/* skip over this message in the input buffer */
		b_in.position(b_in.position() + l);
		return p;
	}

	public static int read(PktInpChannel chnl, ByteBuffer b)
		throws IOException, PktInpChannel.IncompleteBufferReadException
	{
	int start = b.position();
	int m_postsize;
		
		b.limit(start + CA_HDR_SIZE);
		chnl.getPkt(b);
		b.position(start + M_POSTSIZE_OFF);
		m_postsize = b.getShort() & 0xffff;
		b.position(start + CA_HDR_SIZE);
		if ( m_postsize > 0 ) {
			b.limit(start + CA_HDR_SIZE + m_postsize);
			chnl.getPkt(b);
		}
		return CA_HDR_SIZE + m_postsize;
	}

	protected int get_s(int p)
	{
	int rval;

		b.position(p);
		rval = b.getShort() & 0xffff;
		return rval;
	}

	protected void set_s(int p, int v)
	{
		b.position(p);
		b.putShort((short)(v & 0xffff));
	}


	protected int get_i(int p)
	{
	int rval;

		b.position(p);
		rval = b.getInt();
		return rval;
	}

	protected void set_i(int p, int v)
	{
		b.position(p);
		b.putInt(v);
	}

	class CaHdr {
		int   m_cmmd;
		int   m_postsize;
		int   m_dataType;
		int   m_count;
		int   m_cid;
		int   m_available;

		void read()
		{
			b.rewind();
			m_cmmd      = (b.getShort() & 0xffff);
			m_postsize  = (b.getShort() & 0xffff);
			m_dataType  = (b.getShort() & 0xffff);
			m_count     = (b.getShort() & 0xffff);
			m_cid       = b.getInt();
			m_available = b.getInt();
		}
	}

	public int hack(int srv_addr, int srv_port, boolean override, boolean doit)
	{
		int   rval = 0;
		int   m_cmmd = get_m_cmmd();
		
		if (    doit
		     && CA_PROTO_SEARCH == m_cmmd 
             && get_m_postsize()  >= 2
				/* minor version is first 2 bytes of payload */
             && get_s(16)       >= CA_MINOR_PROTO_REV_8 ) {
			if ( 0 != srv_port && (override || 0 == get_m_dataType()) ) {
				set_m_dataType(srv_port);
			}
			int m_cid = get_m_cid();
			if (    CaxyConst.INADDR_ANY != srv_addr
                 && (    override
                      || 0xffffffff == m_cid
                      || 0x00000000 == m_cid ) ) {
				set_m_cid( srv_addr );
			}
		} else if ( REPEATER_CONFIRM == m_cmmd ) {
			rval = REPEATER_CONFIRM;
		} else if ( REPEATER_REGISTER == m_cmmd ) {
			/* Should never get here; the repeater itself should not
			 * fan these out.
			 */
			rval = REPEATER_REGISTER;
		}
		return rval;
	}

	public void dump_hdr()
	{
		System.err.println("CA HDR:");
		System.err.format ("m_cmmd     :     0x%04x\n", get_m_cmmd());
		System.err.format ("m_postsize :     0x%04x\n", get_m_postsize());
		System.err.format ("m_dataType :     0x%04x\n", get_m_dataType());
		System.err.format ("m_count    :     0x%04x\n", get_m_count());
		System.err.format ("m_cid      : 0x%08x\n",     get_m_cid());
		System.err.format ("m_available: 0x%08x\n",     get_m_available());
	}

	public void dump(int debug)
	{
	int i,l;
		dump_hdr();
		System.err.println("PAYLOAD:");

		b.position(CA_HDR_SIZE);
		l = b.remaining();
		for ( i=0; i<l; ) {
			System.err.format("0x%02x ", b.get());
			i++;
			if ( 0 == (i & 0xf) )
				System.err.println();
		}
		System.err.println();
		System.err.println("*****");
	}

	public int get_m_cmmd()      { return get_s(M_CMMD_OFF);       }
	public int get_m_postsize()  { return get_s(M_POSTSIZE_OFF);   }
	public int get_m_dataType()  { return get_s(M_DATATYPE_OFF);   }
	public int get_m_count()     { return get_s(M_COUNT_OFF);      }
	public int get_m_cid()       { return get_i(M_CID_OFF);        }
	public int get_m_available() { return get_i(M_AVAILABLE_OFF);  }

	public void set_m_cmmd(int v)      { set_s(M_CMMD_OFF     ,v); }
	public void set_m_postsize(int v)  { set_s(M_POSTSIZE_OFF ,v); }
	public void set_m_dataType(int v)  { set_s(M_DATATYPE_OFF ,v); }
	public void set_m_count(int v)     { set_s(M_COUNT_OFF    ,v); }
	public void set_m_cid(int v)       { set_i(M_CID_OFF      ,v); }
	public void set_m_available(int v) { set_i(M_AVAILABLE_OFF,v); }

	public void out(PktOutChannel chnl, SocketAddress peer)
		throws IOException, PktOutChannel.IncompleteBufferWrittenException
	{
		b.rewind();
		chnl.putBuf(b, peer);
	}

	public void out(DatagramChannel chnl, SocketAddress peer)
		throws IOException
	{
		b.rewind();
		chnl.send( b, peer );
	}
}
