package br.com.confeitaria.support;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Captura os e-mails enviados em vez de usar SMTP; pode simular falha do servidor de e-mail. */
public class RecordingMailSender extends JavaMailSenderImpl {

    private final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failing;

    @Override
    public void send(SimpleMailMessage... messages) {
        if (failing) {
            throw new MailSendException("SMTP indisponível (simulado)");
        }
        sent.addAll(List.of(messages));
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    public List<SimpleMailMessage> sent() {
        return List.copyOf(sent);
    }

    public List<SimpleMailMessage> sentWithSubject(String subject) {
        return sent.stream().filter(m -> subject.equals(m.getSubject())).toList();
    }

    public void clear() {
        sent.clear();
        failing = false;
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
