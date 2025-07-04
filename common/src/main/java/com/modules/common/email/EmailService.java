package com.modules.common.email;

import com.modules.common.logs.errorlog.ErrorLog;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TemplateEngine templateEngine;


    public boolean sendEmail(String to, String subject, String templateName, Context context) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(new InternetAddress("marcoassenza98@gmail.com"));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(getHtmlContent(templateName, context), true);

            mailSender.send(message);
            return true;
        }catch (Exception e){
            ErrorLog.logger.error("Errore invio email, ", e);
            return false;
        }
    }

    private String getHtmlContent(String templateName, Context context) {
        return templateEngine.process(templateName, context);
    }

    private boolean sendEmail(String to, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(new InternetAddress("marcoassenza98@gmail.com"));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);

            mailSender.send(message);
            return true;
        }catch (Exception e){
            ErrorLog.logger.error("Errore invio email, ", e);
            return false;
        }
    }

    public String sendEmail(EmailRequest emailRequest, String template, Map<String, String> values) {
        boolean flag = false;
        try {
            if(template != null) {
                Context context = new Context();
                for(Map.Entry<String, String> value : values.entrySet()){
                    context.setVariable(value.getKey(), value.getValue());
                }
                flag = sendEmail(emailRequest.getTo(), emailRequest.getSubject(), template, context);
            }else{
                flag = sendEmail(emailRequest.getTo(), emailRequest.getSubject(), emailRequest.getContent());
            }
        } catch (Exception e){
            return "Error";
        }
        if (flag)
            return "SUCCESS";
        return "Errore invio email";
    }

}
