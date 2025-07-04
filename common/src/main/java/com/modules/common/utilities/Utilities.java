package com.modules.common.utilities;

import com.modules.common.logs.errorlog.ErrorLog;
import com.modules.common.model.LongInteger;
import com.modules.common.model.OptionInProduct;
import com.modules.common.model.enums.TypeEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.Writer;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.Key;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class Utilities {
    private final String CARATTERI = "ABCDEFGHJKMNPQRSTUVWXYZ123456789";
    private final int LUNGHEZZA_CODICE = 8;
    private final SecureRandom random = new SecureRandom();

    public void print(String msg) {
        //log.info("ho stampato");
        System.out.println(msg);
    }

    public String convertLongListToString(List<Long> longList) {
        if (longList == null || longList.isEmpty())
            return "";
        StringBuilder str = new StringBuilder("|");
        for (Long l : longList) {
            str.append(l.toString()).append("|");
        }
        return str.toString();
    }

    public String convertIntListToString(List<Integer> intList) {
        if (intList == null || intList.isEmpty())
            return "";
        StringBuilder str = new StringBuilder("|");
        for (Integer l : intList) {
            str.append(l.toString()).append("|");
        }
        return str.toString();
    }

    public List<Integer> convertStringToIntList(String str) {
        if (str == null || str.isEmpty())
            return new ArrayList<>();

        str = clearString(str);

        List<Integer> intList = new ArrayList<>();
        for (String s : str.split("\\|")) {
            intList.add(Integer.parseInt(s));
        }

        return intList;
    }

    public List<Long> convertStringToLongList(String str) {
        if (str == null || str.isEmpty())
            return new ArrayList<>();

        str = clearString(str);

        List<Long> longList = new ArrayList<>();
        for (String s : str.split("\\|")) {
            longList.add(Long.parseLong(s));
        }

        return longList;
    }

    public List<LongInteger> convertStringToLongIntegerList(String str) {
        if (str == null || str.isEmpty())
            return new ArrayList<>();

        str = clearString(str);

        List<LongInteger> longList = new ArrayList<>();
        for (String s : str.split("\\|")) {
            String[] split = s.split(":");
            longList.add(new LongInteger(Long.parseLong(split[0]), Integer.parseInt(split[1])));
        }

        return longList;
    }

    public List<Long> removeFromLongList(List<Long> originalLongList, List<Long> toRemoveLongList) {
        for (Long l : toRemoveLongList) {
            originalLongList.remove(l);
        }
        return originalLongList;
    }

    public List<Integer> removeFromIntList(List<Integer> originalIntList, List<Integer> toRemoveIntList) {
        for (Integer i : toRemoveIntList) {
            originalIntList.remove(i);
        }
        return originalIntList;
    }


    private String clearString(String str) {
        if (str.charAt(0) == '|')
            str = str.substring(1);

        if (str.charAt(str.length() - 1) == '|')
            str = str.substring(0, str.length() - 1);

        return str;
    }

    /**
     * Ripulisce la lista di allergeni eliminando possibili id non esistenti
     */
    public List<Integer> checkAllergens(List<Integer> list) {
        list.removeIf(i -> i <= 0 || i > 14);
        return list;
    }

    public List<OptionInProduct> convertStringToOptionInProduct(String str) {
        if (str == null || str.isEmpty())
            return new ArrayList<>();
        List<String> list = Arrays.stream(str.split(";")).collect(Collectors.toList());
        List<OptionInProduct> optionInProductList = new ArrayList<>();
        for (String s : list) {
            OptionInProduct option = new OptionInProduct(s, true);
            if (!option.getName().equals("-1") && option.getPrice() != -1) {
                optionInProductList.add(option);
            }
        }
        return optionInProductList;
    }

    public String convertOptionInProductToString(List<OptionInProduct> options) {
        if (options == null || options.isEmpty())
            return "";
        // Controllo che ci sia un solo default, se presenti piu di due prendo solo il primo. Se non presente nemmeno uno metto il primo nella lista
        boolean found = false;
        for (OptionInProduct op : options) {
            if (found)
                op.setIsDefault(false);
            if (op.isIsDefault() && !found)
                found = true;
        }
        if (!found)
            options.get(0).setIsDefault(true);
        StringBuilder str = new StringBuilder();
        for (OptionInProduct op : options) {
            str.append(op.convertInString()).append(";");
        }
        return str.toString();
        //return str.substring(str.length() - 1);
    }


    public String generateCode() {
        StringBuilder codice = new StringBuilder(LUNGHEZZA_CODICE);
        for (int i = 0; i < LUNGHEZZA_CODICE; i++) {
            int index = random.nextInt(CARATTERI.length());
            codice.append(CARATTERI.charAt(index));
        }
        return codice.toString();
    }

    public String generateOtpEmailCode() {
        StringBuilder codice = new StringBuilder(8);
        for (int i = 0; i < LUNGHEZZA_CODICE; i++) {
            int index = random.nextInt(CARATTERI.length());
            codice.append(CARATTERI.charAt(index));
        }

        LocalDateTime localDate = LocalDateTime.now();
        String year = String.valueOf(localDate.getYear());
        year = year.length() == 4 ? year.substring(2) : year;
        String today = leftZeroPadding(year, 2) +
                leftZeroPadding(String.valueOf(localDate.getMonthValue()), 2) +
                leftZeroPadding(String.valueOf(localDate.getDayOfMonth()), 2) +
                leftZeroPadding(String.valueOf(localDate.getHour()), 2) +
                leftZeroPadding(String.valueOf(localDate.getMinute()), 2);
        long todayLong = Long.parseLong(today);
        long multip = generateRandomLong(1000, 9999);
        long result = todayLong * multip;
        return codice.toString().concat(String.valueOf(multip)).concat(String.valueOf(result));
    }

    public LocalDateTime decodeOtpEmailCode_getdate(String code){
        long multip = Long.parseLong(code.substring(LUNGHEZZA_CODICE, LUNGHEZZA_CODICE + 4));
        long value = Long.parseLong(code.substring(LUNGHEZZA_CODICE + 4));
        String dateString = String.valueOf(value / multip);
        int year = Integer.parseInt("20" + dateString.substring(0, 2));
        int month = Integer.parseInt(dateString.substring(2, 4));
        int day = Integer.parseInt(dateString.substring(4, 6));
        int hour = Integer.parseInt(dateString.substring(6, 8));
        int minute = Integer.parseInt(dateString.substring(8, 10));
        return LocalDateTime.of(year, month, day, hour, minute);
    }

    public long generateRandomLong(long min, long max){
        Random random = new Random();
        long numeroInIntervallo = random.nextLong(max - min + 1) + min;
        return numeroInIntervallo;
    }

    public String leftZeroPadding(String input, int len) {
        if (input.length() >= len) {
            return input;
        }
        for (int i = input.length(); i < len; i++) {
            input = "0" + input;
        }
        return input;
    }

    public boolean moveFile(String from, String to) {
        Path sourcePath = Paths.get(from);
        Path destinationPath = Paths.get(to);

        try {
            Files.move(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("File spostato con successo da " + from + " a " + to);
        } catch (IOException e) {
            System.err.println("Errore durante lo spostamento del file: " + e.getMessage());
            return false;
        }
        return true;
    }

    public boolean deleteFile(String from) {
        Path filePath = Paths.get(from);

        try {
            Files.delete(filePath);
            System.out.println("File eliminato con successo: " + from);
        } catch (NoSuchFileException e) {
            System.err.println("Il file non esiste: " + from);
            return false;
        } catch (IOException e) {
            System.err.println("Errore durante l'eliminazione del file: " + e.getMessage());
            return false;
        }
        return true;
    }

    public byte[] encrypt(String input, String password) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        Key key = new SecretKeySpec(password.getBytes(StandardCharsets.UTF_8), "AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
    }

    public boolean createDirectoryIfNotExists(String path) {
        File file2 = new File(path);
        if (file2.exists())
            return true;
        return file2.mkdir();
    }

    public boolean generateAndSave(String localname, boolean table, String idTable, String url, String path) {
        try {
            String qrCodeText = (table) ? url + localname + "/" + idTable + "/Categories" : url + localname; // Testo per il quale si vuole generare il codice QR
            //String filePath = "qrcode.png"; // Percorso del file in cui salvare l'immagine QR
            String filePath = (table) ? "qrcode_".concat(idTable).concat(".png") : "qrcode.png";

            int width = 300; // Larghezza dell'immagine QR
            int height = 300; // Altezza dell'immagine QR
            String fileType = "png"; // Tipo di file dell'immagine QR

            File directory = new File(path + "/" + localname + "/QRCode/");
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Salva l'immagine nel percorso specificato
            File qrFile = new File(directory, filePath);

            //File qrFile = new File(FileManager.generalPathImg.concat(""));
            generateQRCode(qrFile, qrCodeText, width, height, fileType);
            System.out.println("QR Code generato con successo!");
            return true;
        } catch (Exception e) {
            ErrorLog.logger.error("Errore generazione QRCode ", e);
        }
        return false;
    }

    private void generateQRCode(File qrFile, String qrCodeText, int width, int height, String fileType) {
        try {
            Writer writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(qrCodeText, BarcodeFormat.QR_CODE, width, height, getHints());
            Path path = FileSystems.getDefault().getPath(qrFile.getAbsolutePath());
            MatrixToImageWriter.writeToPath(bitMatrix, fileType, path);
        } catch (Exception e) {
            ErrorLog.logger.error("Errore generazione QRCode ", e);
        }
    }

    /**
     * Genera un QR Code in memoria e lo restituisce come oggetto MultipartFile.
     *
     * @param qrCodeText Testo da codificare nel QR Code.
     * @param width      Larghezza dell'immagine del QR Code in pixel.
     * @param height     Altezza dell'immagine del QR Code in pixel.
     * @param fileType   Il formato dell'immagine (es. "PNG", "JPG"). Case-insensitive.
     * @return Un oggetto MultipartFile che rappresenta il QR Code, o null in caso di errore.
     */
    public MultipartFile generateQRCodeAsMultipartFile(String qrCodeText, int width, int height, String fileType) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            Writer writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(qrCodeText, BarcodeFormat.QR_CODE, width, height, Collections.emptyMap());

            MatrixToImageWriter.writeToStream(bitMatrix, fileType, baos);

            byte[] qrCodeBytes = baos.toByteArray();

            String fileName = "qrcode-" + System.currentTimeMillis() + "." + fileType.toLowerCase();
            String contentType = "image/" + fileType.toLowerCase();

            return new InMemoryMultipartFile(qrCodeBytes, "file", fileName, contentType);

        } catch (Exception e) {
            ErrorLog.logger.error("Errore generazione QRCode ", e);
            return null;
        }
    }

    private Map<EncodeHintType, Object> getHints() {
        Map<EncodeHintType, Object> hints = new HashMap<EncodeHintType, Object>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        return hints;
    }

    public byte[] optimizeImage(MultipartFile imageFile) {
        try {
            byte[] webpBytes = imageFile.getBytes();
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(webpBytes));

            // Specifica la dimensione desiderata dell'immagine ottimizzata
            int targetSize = 1024; // 1 KB
            long length = imageFile.getBytes().length;
            double resize = 0.5;
            if (length > 7000000) {
                resize = 0.2;
            } else if (length > 5000000) {
                resize = 0.3;
            } else if (length > 3145728) {
                resize = 0.4;
            } else if (length < 800000) {
                resize = 0.9;
            }
            // Ridimensiona l'immagine
            System.out.println("test");
            BufferedImage resizedImage = Thumbnails.of(originalImage)
                    .size(targetSize, targetSize)
                    .outputQuality(resize)
                    .asBufferedImage();

            // Converti l'immagine ridimensionata in formato WebP
            return convertToWebP(resizedImage);

        } catch (Exception e) {
            ErrorLog.logger.error("Errore ottimizzazione immagine. ", e);
        }
        return null;
    }

    private byte[] convertToWebP(BufferedImage image) {
        try {
            ImageIO.scanForPlugins();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            // Utilizza ImageIO per scrivere l'immagine WebP
            ImageIO.write(image, "webp", outputStream);

            return outputStream.toByteArray();
        } catch (IOException e) {
            ErrorLog.logger.error("Errore conversione in webp ", e);
        }
        return null;
    }

    public List<OptionInProduct> convertOptionsRequestToPtions(List<String> optionsJsonList) {
        List<OptionInProduct> optionList = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            for (String optionJson : optionsJsonList) {
                optionJson = optionJson.replaceAll(";", ",");
                OptionInProduct option = objectMapper.readValue(optionJson, OptionInProduct.class);
                optionList.add(option);
            }
            return optionList;
        } catch (IOException e) {
            return null;
        }
    }

}
