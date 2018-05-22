package org.davidmoten.rx2.io.internal;

import org.davidmoten.rx2.io.internal.Server;
import org.junit.Test;

import com.github.davidmoten.junit.Asserts;

public class ServerTest {
    @Test
    public void isUtilityClass() {
        Asserts.assertIsUtilityClass(Server.class);
    }
}
