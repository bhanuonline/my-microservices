package designpattern.builderDesignPattern;

public class ClassicGoFBuilder {
    public static void main(String[] args) {
        ReportDirector director= new ReportDirector();
        HtmlReportBuilder htmlR=new HtmlReportBuilder();
        director.constructAnnualReport(htmlR);
        System.out.println(htmlR.getReport());

    }
}

// Abstract builder interface
interface ReportBuilder {
    void addTitle(String title);
    void addSection(String heading, String content);
    void addFooter(String text);
    String getReport();
}

// Concrete builder: PDF
class PdfReportBuilder implements ReportBuilder {
    private StringBuilder sb = new StringBuilder();
    public void addTitle(String t) { sb.append("[PDF TITLE] " + t + "\n"); }
    public void addSection(String h, String c) { sb.append("[PDF H1] "+h+"\n"+c+"\n"); }
    public void addFooter(String f) { sb.append("[PDF FOOTER] "+f); }
    public String getReport() { return sb.toString(); }
}

// Concrete builder: HTML
class HtmlReportBuilder implements ReportBuilder {
    private StringBuilder sb = new StringBuilder();
    public void addTitle(String t) { sb.append("<h1>"+t+"</h1>"); }
    public void addSection(String h, String c) { sb.append("<h2>"+h+"</h2><p>"+c+"</p>"); }
    public void addFooter(String f) { sb.append("<footer>"+f+"</footer>"); }
    public String getReport() { return sb.toString(); }
}

// Director: same algorithm, different builders
class ReportDirector {
    public void constructAnnualReport(ReportBuilder b) {
        b.addTitle("Annual Report 2024");
        b.addSection("Revenue", "$10M total...");
        b.addSection("Growth", "25% YoY...");
        b.addFooter("Confidential");
    }
}
