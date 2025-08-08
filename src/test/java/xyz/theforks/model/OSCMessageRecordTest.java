package xyz.theforks.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OSCMessageRecordTest {

    @Test
    void testDefaultConstructor() {
        OSCMessageRecord record = new OSCMessageRecord();
        assertNull(record.getAddress());
        assertNull(record.getArguments());
        assertEquals(0L, record.getTimestamp());
    }

    @Test
    void testConstructorWithParameters() {
        String address = "/test/message";
        Object[] arguments = {1.0f, "hello", 42};
        
        OSCMessageRecord record = new OSCMessageRecord(address, arguments);
        
        assertEquals(address, record.getAddress());
        assertArrayEquals(arguments, record.getArguments());
        assertTrue(record.getTimestamp() > 0);
        assertTrue(System.currentTimeMillis() - record.getTimestamp() < 1000); // Within 1 second
    }

    @Test
    void testSettersAndGetters() {
        OSCMessageRecord record = new OSCMessageRecord();
        
        String address = "/test/setter";
        Object[] arguments = {3.14f, "world"};
        long timestamp = 1234567890L;
        
        record.setAddress(address);
        record.setArguments(arguments);
        record.setTimestamp(timestamp);
        
        assertEquals(address, record.getAddress());
        assertArrayEquals(arguments, record.getArguments());
        assertEquals(timestamp, record.getTimestamp());
    }

    @Test
    void testEmptyArguments() {
        String address = "/test/empty";
        Object[] emptyArgs = {};
        
        OSCMessageRecord record = new OSCMessageRecord(address, emptyArgs);
        
        assertEquals(address, record.getAddress());
        assertArrayEquals(emptyArgs, record.getArguments());
        assertEquals(0, record.getArguments().length);
    }

    @Test
    void testNullArguments() {
        String address = "/test/null";
        
        OSCMessageRecord record = new OSCMessageRecord(address, null);
        
        assertEquals(address, record.getAddress());
        assertNull(record.getArguments());
    }
}