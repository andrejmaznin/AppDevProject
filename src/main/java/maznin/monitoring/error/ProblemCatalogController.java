package maznin.monitoring.error;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Машиночитаемый справочник типов проблем — источник данных для раздела
 * «База знаний» во фронтенде.
 *
 * <p>Эндпоинт открыт без аутентификации: каталог — статическая справочная
 * информация без персональных данных, и он должен быть доступен в том числе
 * для объяснения ошибок входа.</p>
 */
@RestController
@RequestMapping("/api/v1/problems")
public class ProblemCatalogController {

    /**
     * Описание одного типа проблемы для базы знаний.
     *
     * @param type стабильный URI типа (значение поля {@code type} в ответах об ошибках)
     * @param status HTTP-статус
     * @param title человекочитаемое имя класса проблемы
     * @param description когда и почему возникает
     * @param remediation действия для устранения
     */
    public record ProblemDescriptor(String type, int status, String title,
                                    String description, String remediation) {

        static ProblemDescriptor of(ProblemType problemType) {
            return new ProblemDescriptor(
                    problemType.uri().toString(),
                    problemType.getStatus().value(),
                    problemType.getTitle(),
                    problemType.getDescription(),
                    problemType.getRemediation()
            );
        }
    }

    /**
     * Полный каталог известных типов проблем приложения.
     */
    @GetMapping
    public Flux<ProblemDescriptor> getCatalog() {
        return Flux.fromArray(ProblemType.values()).map(ProblemDescriptor::of);
    }
}
