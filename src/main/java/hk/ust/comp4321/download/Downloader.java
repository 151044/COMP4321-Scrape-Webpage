package hk.ust.comp4321.download;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

public class Downloader {
    public static void main(String[] args) throws IOException {
        String templateUrl = "https://w5.ab.ust.hk/wcq/cgi-bin/%s/";
        List<String> yes = List.of("yes", "y");

        Scanner in = new Scanner(System.in);
        LocalDateTime time = LocalDateTime.now();
        int year = time.getYear();
        int month = time.getMonthValue(); // 1 to 12
        int day = time.getDayOfMonth();
        String monthUrl = String.valueOf(month < 9 ? year % 100 - 1: year % 100);
        if (month < 2 && day < 20) {
            monthUrl += "20";
        } else if (month < 6) {
            monthUrl += "30";
        } else if (month < 8) {
            monthUrl += "40";
        } else {
            monthUrl += "10";
        }

        String url = String.format(templateUrl, monthUrl);
        System.out.print("Going to fetch from " + url +". Proceed? (y/n) ");
        String yn = in.nextLine();
        if (!yes.contains(yn.toLowerCase())) {
            System.out.print("Please input alternative year ID (in the form of 2310): ");
            monthUrl = in.nextLine();
            url = String.format(templateUrl, monthUrl);
        }

        Path fileRoot = Path.of("data"); // prep dir for writing
        /* // if (Files.exists(fileRoot)) {
            try (Stream<Path> p = Files.walk(fileRoot)) {
                p.filter(Files::isRegularFile)
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
        } */
        // Files.deleteIfExists(fileRoot);
        Files.createDirectories(fileRoot);

        String finalUrl = url;
        System.out.println("Downloading from " + finalUrl + ".");
        Document root = Jsoup.connect(finalUrl).get();
        Element depts = root.getElementsByClass("depts").get(0);
        List<String> deptList = depts.children().eachText();
        String finalMonthUrl = monthUrl;
        deptList.forEach(str -> {
            try {
                Files.writeString(Path.of("data/" + str + "-" + finalMonthUrl +".html"), Jsoup.connect(finalUrl + "subject/" + str).get().html());
            } catch (IOException e) {
                System.err.println("Unable to write file: ");
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });

    }

}
