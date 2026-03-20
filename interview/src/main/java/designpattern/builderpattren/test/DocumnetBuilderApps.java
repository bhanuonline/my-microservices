package designpattern.builderpattren.test;

import interview.InterviewApplication;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Map;

class Documents {
    String header;
    String body;
    String footer;
}

interface DocumentsBuilder {
    void buildHeader();

    void buildBody();

    void buildFooter();

    Documents getDocuments();
}

@Component("PDF")
class PdfDocumentsBuilder implements DocumentsBuilder {

    @Override
    public void buildHeader() {

    }

    @Override
    public void buildBody() {

    }

    @Override
    public void buildFooter() {

    }

    @Override
    public Documents getDocuments() {
        return new Documents();
    }
}

@Component("EXCEL")
class ExcelDocumentsBuilder implements DocumentsBuilder {

    @Override
    public void buildHeader() {

    }

    @Override
    public void buildBody() {

    }

    @Override
    public void buildFooter() {

    }

    @Override
    public Documents getDocuments() {
        return new Documents();
    }
}

@Component
class DocumentsBuilderFactory {

    private final Map<String, DocumentsBuilder> builders;

    public DocumentsBuilderFactory(Map<String, DocumentsBuilder> builders) {
        this.builders = builders;
    }

    public DocumentsBuilder getBuilder(String type) {
        DocumentsBuilder builder = builders.get(type.toUpperCase());
        if (builder == null) {
            throw new IllegalArgumentException("Unsupported report type");
        }
        return builder;
    }
}

@Component
class DocumentsDirector {

    public Documents construct(DocumentsBuilder builder) {
        builder.buildHeader();
        builder.buildBody();
        builder.buildFooter();
        return builder.getDocuments();
    }
}

@Service
@RequiredArgsConstructor
class ReportService {

    private final DocumentsDirector director;
    private final DocumentsBuilderFactory factory;

    public Documents generateReport(String type) {
        DocumentsBuilder builder = factory.getBuilder(type);
        return director.construct(builder);
    }
}


//@SpringBootApplication(
//        exclude = {
//                DataSourceAutoConfiguration.class,
//                HibernateJpaAutoConfiguration.class
//        }
//)
public class DocumnetBuilderApps {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(DocumnetBuilderApps.class, args);
        System.out.println(context);
    }
}
