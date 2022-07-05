package org.example.test.binlog;

import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.Event;
import com.github.shyiko.mysql.binlog.event.EventData;
import com.github.shyiko.mysql.binlog.event.EventHeader;
import com.github.shyiko.mysql.binlog.event.EventType;
import com.github.shyiko.mysql.binlog.event.deserialization.EventDataDeserializer;
import com.github.shyiko.mysql.binlog.event.deserialization.EventDeserializer;
import com.github.shyiko.mysql.binlog.io.ByteArrayInputStream;
import org.junit.Test;

import java.io.IOException;
import java.util.EventListener;

public class BInLogTest {

    @Test
    public void test_() throws Exception{
        BinaryLogClient client = new BinaryLogClient("localhost",3306,"root","root");
        EventDeserializer eventDeserializer = new EventDeserializer();
        eventDeserializer.setCompatibilityMode(
                EventDeserializer.CompatibilityMode.DATE_AND_TIME_AS_LONG,
                EventDeserializer.CompatibilityMode.CHAR_AND_BINARY_AS_BYTE_ARRAY
        );
        eventDeserializer.setEventDataDeserializer(EventType.EXT_DELETE_ROWS, new EventDataDeserializer() {
            @Override
            public EventData deserialize(ByteArrayInputStream inputStream) throws IOException {
                return null;
            }
        });
        client.setEventDeserializer(eventDeserializer);
        client.registerEventListener(new BinaryLogClient.EventListener() {
            @Override
            public void onEvent(Event event) {
                System.out.println(event);
                EventData data = event.getData();
                EventHeader header = event.getHeader();
                EventType eventType = header.getEventType();
                System.out.println(eventType);

            }
        });
        client.connect(3000);
        System.in.read();
    }
}
