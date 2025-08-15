package ch.rasia.extension.resource.git;

import java.io.File;

public class GitUtil {
	private static final long[] byteTable = createLookupTable();
	private static final long HSTART = 0xBB40E64DA205B064L;
	private static final long HMULT = 7664345821815920749L;
	
	
	public static long create64BitHash(CharSequence cs) {
		long h = HSTART;
		final long hmult = HMULT;
		final long[] ht = byteTable;
		final int len = cs.length();
		for (int i = 0; i < len; i++) {
			char ch = cs.charAt(i);
			h = (h * hmult) ^ ht[ch & 0xff];
			h = (h * hmult) ^ ht[(ch >>> 8) & 0xff];
		}
		if(h<0)
			return 0-h;
		return h;
	}
	
	private static final long[] createLookupTable() {
		long[] _byteTable = new long[256];
		long h = 0x544B2FBACAAF1684L;
		for (int i = 0; i < 256; i++) {
			for (int j = 0; j < 31; j++) {
				h = (h >>> 7) ^ h;
				h = (h << 11) ^ h;
				h = (h >>> 10) ^ h;
			}
			_byteTable[i] = h;
		}
		return _byteTable;
	}

	public static void delete(File file) {
		if(file==null) return;
		if(file.isFile()) {
			file.delete();
		}
		else if(file.isDirectory()) {
			File[] children = file.listFiles();
			for(File child:children) {
				delete(child);
			}
			file.delete();
		}
		else {
			file.delete();
		}
	}

	public static String create64BitHashAsString(CharSequence cs) {
		return Long.toString(create64BitHash(cs), Character.MAX_RADIX);
	}
	
	public static String create64BitHashAsString(CharSequence cs, int radix) {
		return Long.toString(create64BitHash(cs), radix);
	}
	
	public static String getSystemPropOrEnvVar(String name, String defaultValue) {
		// env
		String value = System.getenv(name);
		if(!lucee.loader.util.Util.isEmpty(value))
			return value;

		// prop
		value = System.getProperty(name);
		if(!lucee.loader.util.Util.isEmpty(value))
			return value;

		// env 2
		name = name.replace('.', '_').toUpperCase();
		value = System.getenv(name);
		if(!lucee.loader.util.Util.isEmpty(value))
			return value;

		return defaultValue;
	}
}
