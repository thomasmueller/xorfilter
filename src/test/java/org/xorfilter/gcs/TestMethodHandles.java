package org.xorfilter.gcs;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

public class TestMethodHandles {
	public static void main(String... args) {
    	VarHandle vh = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.nativeOrder());
    	byte[] data = new byte[16];
    	data[0] = (byte) 0xff;
    	data[1] = (byte) 0x12;
    	data[2] = (byte) 0xde;
    	data[3] = (byte) 0x30;
    	System.out.println(Long.toHexString((long) vh.get(data, 0)));
	}
}
