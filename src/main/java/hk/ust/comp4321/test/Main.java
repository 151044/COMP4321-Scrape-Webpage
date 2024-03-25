package hk.ust.comp4321.test;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws IOException {
        Document parse = Jsoup.parse(Path.of("test-page.htm").toFile());
        System.out.println(parse.body().wholeText());
    }
}
