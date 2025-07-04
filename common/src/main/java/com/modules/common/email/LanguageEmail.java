package com.modules.common.email;

import java.util.HashMap;
import java.util.Map;

public class LanguageEmail {
    private final static String EN = "EN";
    private final static String IT = "IT";
    private final static String ES = "ES";

    public static Map<String, String> getCodeRegistration(String language) {
        Map<String, String> map = new HashMap<>();
        if (language.equals(IT)) {
            map.put("title", "Conferma Account");
            map.put("par0", "Benvenuto/a ");
            map.put("par1", "Grazie per esserti registrato, sei quasi pronto per iniziare.");
            map.put("par2", "Clicca sul pulsante qui sotto per verificare il tuo indirizzo e-mail e usufruire dei servizi per il tuo locale!");
            map.put("par3", "Clicca qui per confermare");
            map.put("par4", "Grazie,");
            map.put("par5", "The Weitmenu Team");
        } else if (language.equals(ES)) {
            map.put("title", "Confirmar Cuenta");
            map.put("par0", "¡Bienvenido/a ");
            map.put("par1", "Gracias por registrarte, casi estás listo para empezar.");
            map.put("par2", "Haz clic en el botón de abajo para verificar tu dirección de correo electrónico y disfrutar de los servicios para tu local.");
            map.put("par3", "Haz clic aquí para confirmar");
            map.put("par4", "Gracias,");
            map.put("par5", "El equipo de Weitmenu");
        } else {
            map.put("title", "Confirm Account");
            map.put("par0", "Welcome ");
            map.put("par1", "Thank you for registering, you're almost ready to get started.");
            map.put("par2", "Click the button below to verify your email address and access the services for your establishment!");
            map.put("par3", "Click here to confirm");
            map.put("par4", "Thank you,");
            map.put("par5", "The Weitmenu Team");
        }
        return map;
    }

    public static Map<String, String> getAbbonamentoInScadenza(String language) {
        Map<String, String> map = new HashMap<>();
        if (language.equals(IT)) {
            map.put("title", "Scadenza abbonamento");
            map.put("par0", "Ciao ");
            map.put("par1", "Ti ricordiamo che l'abbonamento verra' rinnovato tra 10 giorni.");
            map.put("par2", "Ti informiamo che, nel caso non effettuassi alcuna azione, l'abbonamento verrà automaticamente rinnovato per un altro ciclo.");
            map.put("par3", "Ti consigliamo gentilmente di assicurarti di avere fondi sufficienti nel tuo conto per garantire il corretto addebito e evitare problemi relativi al rinnovo dell'abbonamento.");
            map.put("par4", "Se desideri apportare modifiche al tuo abbonamento o se hai domande riguardo al processo di rinnovo automatico, ti preghiamo di contattarci quanto prima. Saremo lieti di assisterti e fornirti tutte le informazioni necessarie.");
            map.put("par5", "Grazie,");
            map.put("par6", "The Weitmenu Team");
        } else if (language.equals(ES)) {
            map.put("title", "Vencimiento de la suscripción");
            map.put("par0", "Hola ");
            map.put("par1", "Te recordamos que la suscripción se renovará en 10 días.");
            map.put("par2", "Te informamos que, en caso de que no realices ninguna acción, la suscripción se renovará automáticamente por otro ciclo.");
            map.put("par3", "Te recomendamos amablemente asegurarte de tener fondos suficientes en tu cuenta para garantizar el cargo correcto y evitar problemas relacionados con la renovación de la suscripción.");
            map.put("par4", "Si deseas realizar cambios en tu suscripción o tienes preguntas sobre el proceso de renovación automática, por favor contáctanos lo antes posible. Estaremos encantados de ayudarte y proporcionarte toda la información necesaria.");
            map.put("par5", "Gracias,");
            map.put("par6", "El equipo de Weitmenu");
        } else {
            map.put("title", "Subscription Expiration");
            map.put("par0", "Hello ");
            map.put("par1", "We remind you that the subscription will be renewed in 10 days.");
            map.put("par2", "We inform you that if you do not take any action, the subscription will be automatically renewed for another cycle.");
            map.put("par3", "We kindly advise you to ensure that you have sufficient funds in your account to ensure the correct charge and avoid problems related to the renewal of the subscription.");
            map.put("par4", "If you wish to make changes to your subscription or if you have any questions about the automatic renewal process, please contact us as soon as possible. We will be happy to assist you and provide you with all the necessary information.");
            map.put("par5", "Thank you,");
            map.put("par6", "The Weitmenu Team");
        }
        return map;
    }

    public static Map<String, String> getAbbonamentoInScadenzaTrial(String language) {
        Map<String, String> map = new HashMap<>();
        if (language.equals(IT)) {
            map.put("title", "Scadenza prova");
            map.put("par0", "Ciao ");
            map.put("par1", "Ti ricordiamo che il periodo di prova terminera' tra 10 giorni.");
            map.put("par2", "Per evitare l'interruzione del tuo account, ti preghiamo di inserire i dettagli della tua carta di pagamento al piu' presto.");
            map.put("par3", "Aggiornare i tuoi dati è facile: basta accedere al tuo account e seguire le istruzioni per inserire i dettagli della carta.");
            map.put("par4", "Se hai domande o necessiti di assistenza, non esitare a contattarci.");
            map.put("par5", "Grazie,");
            map.put("par6", "The Weitmenu Team");
        } else if (language.equals(ES)) {
            map.put("title", "Scadenza prova");
            map.put("par0", "Ciao ");
            map.put("par1", "Ti ricordiamo che il periodo di prova terminera' tra 10 giorni.");
            map.put("par2", "Per evitare l'interruzione del tuo account, ti preghiamo di inserire i dettagli della tua carta di pagamento al piu' presto.");
            map.put("par3", "Aggiornare i tuoi dati è facile: basta accedere al tuo account e seguire le istruzioni per inserire i dettagli della carta.");
            map.put("par4", "Se hai domande o necessiti di assistenza, non esitare a contattarci.");
            map.put("par5", "Grazie,");
            map.put("par6", "The Weitmenu Team");
        } else {
            map.put("title", "Trial Expiration");
            map.put("par0", "Hello ");
            map.put("par1", "We would like to remind you that the trial period will end in 10 days.");
            map.put("par2", "To avoid any interruption to your account, please provide your payment card details as soon as possible.");
            map.put("par3", "Updating your information is easy: just log in to your account and follow the instructions to enter your card details.");
            map.put("par4", "If you have any questions or need assistance, please do not hesitate to contact us.");
            map.put("par5", "Thank you,");
            map.put("par6", "The Weitmenu Team");
        }
        return map;
    }

    public static Map<String, String> getNewSubscription(String language) {
        Map<String, String> map = new HashMap<>();
        return map;
    }

    public static Map<String, String> getPaymentExpired(String language) {
        Map<String, String> map = new HashMap<>();
        if (language.equals(IT)) {
            map.put("par0", "Pagamento fallito");
            map.put("par1", "Ti contattiamo per informarti che abbiamo riscontrato un problema con il rinnovo automatico del tuo account.");
            map.put("par2", "Purtroppo, il tentativo di addebitare il pagamento per il rinnovo non è andato a buon fine.");
            map.put("par3", "Ti preghiamo gentilmente di effettuare il pagamento del rinnovo entro i prossimi 3 giorni per evitare la sospensione dell'account. Questo ti darà il tempo necessario per risolvere la questione e continuare a beneficiare dei nostri servizi senza interruzioni.");
            map.put("par4", "Se hai domande o necessiti di assistenza, non esitare a contattarci.");
            map.put("par5", "Grazie, ");
            map.put("par6", "The Weitmenu Team");
        } else if (language.equals(ES)) {
            map.put("par0", "Pago fallido");
            map.put("par1", "Te contactamos para informarte que hemos tenido un problema con la renovación automática de tu cuenta.");
            map.put("par2", "Lamentablemente, el intento de cobrar el pago por la renovación no ha tenido éxito.");
            map.put("par3", "Te rogamos que realices el pago de la renovación en los próximos 3 días para evitar la suspensión de la cuenta. Esto te dará tiempo suficiente para resolver el problema y seguir disfrutando de nuestros servicios sin interrupciones.");
            map.put("par4", "Si tienes preguntas o necesitas ayuda, no dudes en contactarnos.");
            map.put("par5", "Gracias, ");
            map.put("par6", "El equipo de Weitmenu");

        } else {
            map.put("par0", "Payment Failed");
            map.put("par1", "We are contacting you to inform you that we encountered an issue with the automatic renewal of your account.");
            map.put("par2", "Unfortunately, the attempt to charge the payment for the renewal was unsuccessful.");
            map.put("par3", "Kindly make the payment for the renewal within the next 3 days to avoid the suspension of your account. This will give you the necessary time to resolve the issue and continue to benefit from our services without interruption.");
            map.put("par4", "If you have any questions or need assistance, please do not hesitate to contact us.");
            map.put("par5", "Thank you, ");
            map.put("par6", "The Weitmenu Team");
        }
        return map;
    }

    public static Map<String, String> getPaymentExpiredSuper(String language) {
        Map<String, String> map = new HashMap<>();
        return map;
    }

    public static Map<String, String> getRecoverPassword(String language) {
        Map<String, String> map = new HashMap<>();
        if (language.equals(IT)) {
            map.put("title", "Recupero password");
            map.put("par0", "Password dimenticata?");
            map.put("par1", "Ciao ");
            map.put("par2", ", abbiamo ricevuto una richiesta di cambio password");
            map.put("par3", "Se non hai fatto nessuna richiesta, ignora questa email. Altrimenti, clicca qui sotto per cambiare la password.");
            map.put("par4", "Cambia password");
            map.put("par5", "Grazie,");
            map.put("par6", "The Weitmenu Team");
        } else if (language.equals(ES)) {
            map.put("title", "Recuperación de contraseña");
            map.put("par0", "¿Olvidaste tu contraseña?");
            map.put("par1", "Hola ");
            map.put("par2", ", hemos recibido una solicitud de cambio de contraseña");
            map.put("par3", "Si no has solicitado ningún cambio, ignora este correo electrónico. De lo contrario, haz clic a continuación para cambiar la contraseña.");
            map.put("par4", "Cambiar contraseña");
            map.put("par5", "Gracias,");
            map.put("par6", "El equipo de Weitmenu");
        } else {
            map.put("title", "Password Recovery");
            map.put("par0", "Forgot your password?");
            map.put("par1", "Hello ");
            map.put("par2", ", we have received a password reset request.");
            map.put("par3", "If you did not make this request, please ignore this email. Otherwise, click the button below to reset your password.");
            map.put("par4", "Reset Password");
            map.put("par5", "Thank you,");
            map.put("par6", "The Weitmenu Team");
        }
        return map;
    }

    public static Map<String, String> getSubscriptionDeleted(String language) {
        Map<String, String> map = new HashMap<>();
        if (language.equals(IT)) {
            map.put("title", "Sospensione account");
            map.put("par0", "Ciao ");
            map.put("par1", "Ti scriviamo per informarti che, purtroppo, dopo il periodo di tempo concesso per il rinnovo dell'account, abbiamo proceduto alla sospensione del tuo account.");
            map.put("par2", "Per riattivare il tuo account e ripristinare l'accesso ai nostri servizi, ti preghiamo di contattarci immediatamente.");
            map.put("par3", "Ci scusiamo per eventuali inconvenienti che questa sospensione potrebbe causarti e ci auguriamo di poter risolvere rapidamente la situazione.");
            map.put("par4", "Grazie,");
            map.put("par5", "The Weitmenu Team");
        } else if (language.equals(ES)) {
            map.put("title", "Suspensión de cuenta");
            map.put("par0", "Hola ");
            map.put("par1", "Te escribimos para informarte que, lamentablemente, después del período de tiempo otorgado para la renovación de la cuenta, hemos procedido a suspender tu cuenta.");
            map.put("par2", "Para reactivar tu cuenta y restablecer el acceso a nuestros servicios, por favor contáctanos de inmediato.");
            map.put("par3", "Lamentamos cualquier inconveniente que esta suspensión pueda causarte y esperamos poder resolver la situación rápidamente.");
            map.put("par4", "Gracias,");
            map.put("par5", "El equipo de Weitmenu");
        } else {
            map.put("title", "Account Suspension");
            map.put("par0", "Hello ");
            map.put("par1", "We are writing to inform you that, unfortunately, after the grace period for account renewal has passed, we have proceeded with the suspension of your account.");
            map.put("par2", "To reactivate your account and restore access to our services, please contact us immediately.");
            map.put("par3", "We apologize for any inconvenience this suspension may cause you and hope to resolve the situation quickly.");
            map.put("par4", "Thank you,");
            map.put("par5", "The Weitmenu Team");
        }
        return map;
    }

    public static Map<String, String> getWaiterInvitation(String language) {
        Map<String, String> map = new HashMap<>();
        if (language.equals(IT)) {
            map.put("title", "Nuovo invito");
            map.put("par0", "Benvenuto/a ");
            map.put("par1", "Ti e' stato essagnato il ruolo di cameriere, sei quasi pronto per iniziare.");
            map.put("par2", "Clicca sul pulsante qui sotto per verificare il tuo indirizzo e-mail e completare la registrazione!");
            map.put("par3", "Clicca qui per confermare");
            map.put("par4", "Grazie,");
            map.put("par5", "The Weitmenu Team");
        } else if (language.equals(ES)) {
            map.put("title", "Nueva invitación");
            map.put("par0", "¡Bienvenido/a ");
            map.put("par1", "Te han asignado el rol de camarero, casi estás listo para empezar.");
            map.put("par2", "¡Haz clic en el botón de abajo para verificar tu dirección de correo electrónico y completar el registro!");
            map.put("par3", "Haz clic aquí para confirmar");
            map.put("par4", "Gracias,");
            map.put("par5", "El equipo de Weitmenu");
        } else if(language.equals(ES)) {
            map.put("title", "Nueva Invitación");
            map.put("par0", "Bienvenido/a ");
            map.put("par1", "Se te ha asignado el rol de camarero/camarera, ya casi estás listo/a para empezar.");
            map.put("par2", "¡Haz clic en el botón de abajo para verificar tu dirección de correo electrónico y completar el registro!");
            map.put("par3", "Haz clic aquí para confirmar");
            map.put("par4", "Gracias,");
            map.put("par5", "El equipo de Weitmenu");
        }else{
            map.put("title", "New Invitation");
            map.put("par0", "Welcome ");
            map.put("par1", "You have been assigned the role of waiter/waitress, you're almost ready to get started.");
            map.put("par2", "Click the button below to verify your email address and complete the registration!");
            map.put("par3", "Click here to confirm");
            map.put("par4", "Thank you,");
            map.put("par5", "The Weitmenu Team");
        }
        return map;
    }
}
