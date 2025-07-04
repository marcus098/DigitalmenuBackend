package com.modules.common.email;

import java.util.Properties;
/*
public class GmailSender {
    public static void main(String[] args) {

        final String username = "marcoassenza98@gmail.com";
        final String password = "ocjwevvagbodinpg"; // usa app password se hai 2FA

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.smtp.ssl.trust", "*");

        Session session = Session.getInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

        try {
            //Message message = new MimeMessage(session);
            //message.setFrom(new InternetAddress("loardmarcus0000@gmail.com"));
            //message.setRecipients(
            //        Message.RecipientType.TO,
            //        InternetAddress.parse("marcoassenza0098@gmail.com")
            //);
            //message.setSubject("Oggetto dell'email");
            //message.setText("Contenuto del messaggio");
            //message.saveChanges(); // Assicurati che gli headers siano aggiornati
            //String messageId = message.getHeader("Message-ID")[0];
            //System.out.println("Message-ID: " + messageId);
//
            //Transport.send(message);
//
            //System.out.println("Email inviata con successo!");
            read();
        } catch (MessagingException e) {
            e.printStackTrace();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void read () throws Exception{
        String host = "imap.gmail.com";
        String username = "marcoassenza98@gmail.com";
        String password = "ocjwevvagbodinpg"; // NON la password normale, ma una App Password!

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", host);
        props.put("mail.imaps.port", "993");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.ssl.trust", "*");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        store.connect(host, username, password);

        // Accedi alla cartella Inviati
        Folder sentFolder = store.getFolder("[Gmail]/Posta inviata");
        sentFolder.open(Folder.READ_ONLY);
        System.out.println(sentFolder.getMessageCount());

        // Leggi gli ultimi 10 messaggi
        Message[] messages = sentFolder.getMessages();
        for (Message message : messages) {
            if(message.getHeader("Message-ID")[0].equals("<940553268.1.1748265088524@DESKTOP-5CO5VLJ>")){
                System.out.println("trovato");
            }
            //System.out.println("Subject: " + message.getSubject());
            //System.out.println("From: " + message.getFrom()[0]);
            //System.out.println("Sent Date: " + message.getSentDate());
            //System.out.println("---------------------------");
        }

        sentFolder.close(false);
        store.close();
    }
}
*/