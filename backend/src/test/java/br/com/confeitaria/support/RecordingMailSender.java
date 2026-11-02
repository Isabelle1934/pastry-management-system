package br.com.confeitaria.support;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Captura os e-mails enviados em vez de usar SMTP. */
public class RecordingMailSender extends JavaMailSenderImpl {

    private final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(SimpleMailMessage... messages) {
        sent.addAll(List.of(messages));
    }

    public List<SimpleMailMessage> sent() {
        return List.copyOf(sent);
    }

    public void clear() {
        sent.clear();
    }

    /** O envio é assíncrono (após o commit): aguarda até {@code count} mensagens. */
    public List<SimpleMailMessage> awaitMessages(int count, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (sent.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        return sent();
    }
}
