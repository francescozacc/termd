package io.modsh.core.ssh;

import io.modsh.core.io.BinaryDecoder;
import io.modsh.core.readline.Action;
import io.modsh.core.readline.Reader;
import io.modsh.core.readline.ActionHandler;
import org.apache.sshd.SshServer;
import org.apache.sshd.server.ChannelSessionAware;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.channel.ChannelDataReceiver;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Readline bootstrap for SSH.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ReadlineBootstrap {

  private static final Pattern LC_PATTERN = Pattern.compile("(?:\\p{Alpha}{2}_\\p{Alpha}{2}\\.)?([^@]+)(?:@.+)?");

  public static void main(String[] args) throws Exception {

    SshServer sshd = SshServer.setUpDefaultServer();

    InputStream inputrc = Reader.class.getResourceAsStream("inputrc");

    class MyCommand implements Command, SessionAware, ChannelSessionAware {

      private BinaryDecoder decoder;
      private Reader reader = new Reader(inputrc);
      private ActionHandler handler = new ActionHandler();

      @Override
      public void setChannelSession(ChannelSession session) {

        session.setDataReceiver(new ChannelDataReceiver() {
          @Override
          public int data(ChannelSession channel, byte[] buf, int start, int len) throws IOException {

            while (len-- > 0) {
              byte i = buf[start++]; // Not taking care of encoding
              if (decoder != null) {
                decoder.onByte(i);
              } else {
                onChar(i);
              }
            }

            while (true) {
              Action action = reader.reduceOnce().popKey();
              if (action != null) {
                handler.handle(action);
              } else {
                break;
              }
            }

            return len;
          }

          @Override
          public void close() throws IOException {

          }
        });
      }

      @Override
      public void setSession(ServerSession session) {
      }

      @Override
      public void setInputStream(InputStream in) {
      }

      @Override
      public void setOutputStream(OutputStream out) {
      }

      @Override
      public void setErrorStream(OutputStream err) {
      }

      @Override
      public void setExitCallback(ExitCallback callback) {
      }

      private void onChar(int c) {
        reader.append(c);
      }

      @Override
      public void start(Environment env) throws IOException {
        String lcctype = env.getEnv().get("LC_CTYPE");
        if (lcctype != null) {
          Charset charset = parseCharset(lcctype);
          decoder = new BinaryDecoder(charset, this::onChar);
        }
      }

      @Override
      public void destroy() {
      }
    }

    sshd.setShellFactory(MyCommand::new);

    sshd.setPort(5000);
    sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider("hostkey.ser"));
    sshd.setPasswordAuthenticator((username, password, session) -> true);
    sshd.start();

  }

  private static Charset parseCharset(String value) {
    Matcher matcher = LC_PATTERN.matcher(value);
    if (matcher.matches()) {
      try {
        return Charset.forName(matcher.group(1));
      }
      catch (Exception ignore) {
      }
    }
    return null;
  }

}
