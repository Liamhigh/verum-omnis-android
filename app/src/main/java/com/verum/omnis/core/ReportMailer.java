package com.verum.omnis.core;

import android.content.Context;

import org.json.JSONObject;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;

/**
 * ReportMailer – sends Verum Omnis R&D feedback packets as signed/timestamped JSON via email.
 */
public class ReportMailer {

    /**
     * Send an analysis report by email, attaching the JSON packet.
     *
     * @param ctx       Android context (for version, if needed)
     * @param smtpHost  SMTP server hostname (e.g. smtp.gmail.com)
     * @param smtpPort  Port (465 for SSL, 587 for STARTTLS)
     * @param username  SMTP login / from-address
     * @param password  SMTP password / app password
     * @param recipient Recipient email
     * @param feedback  Feedback object (JSON diagnostics + directives)
     */
    public static void sendReport(Context ctx,
                                  String smtpHost, int smtpPort,
                                  final String username, final String password,
                                  String recipient,
                                  JSONObject feedback) {
        try {
            // Wrap feedback in packet
            JSONObject packet = new JSONObject();
            packet.put("schema", "verum.omnis.mesh");
            packet.put("templateVersion", "v1");
            packet.put("appVersion", "v5.2.6");
            packet.put("timestampUtc", isoNow());
            packet.put("feedback", feedback);

            // Sign packet
            String body = packet.toString();
            String hash = sha512(body.getBytes("UTF-8"));
            packet.put("sha512", hash);

            // Mail server props
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true"); // STARTTLS
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", String.valueOf(smtpPort));

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            // Build message
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(username));
            msg.setRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
            msg.setSubject("Verum Omnis Analysis Report – " + isoNow());

            // Body text
            msg.setText("Attached is the Verum Omnis analysis packet.\n" +
                    "Hash: " + hash + "\nTimestamp: " + isoNow());

            // Attach JSON packet as file
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText("See attached analysis report.");

            MimeBodyPart attachPart = new MimeBodyPart();
            InputStream jsonStream = new ByteArrayInputStream(packet.toString(2).getBytes("UTF-8"));
            attachPart.setDataHandler(new javax.activation.DataHandler(
                    new javax.activation.DataSource() {
                        @Override
                        public InputStream getInputStream() {
                            return jsonStream;
                        }

                        @Override
                        public OutputStream getOutputStream() {
                            throw new UnsupportedOperationException("Read-only");
                        }

                        @Override
                        public String getContentType() {
                            return "application/json";
                        }

                        @Override
                        public String getName() {
                            return "verum_report.json";
                        }
                    }
            ));
            attachPart.setFileName("verum_report.json");

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(textPart);
            multipart.addBodyPart(attachPart);
            msg.setContent(multipart);

            Transport.send(msg);
            System.out.println("Report email sent to " + recipient);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // === Helpers ===
    private static String sha512(byte[] b) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-512");
        md.update(b);
        byte[] d = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte x : d) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static String isoNow() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
}
