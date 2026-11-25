package ng.speakahouse.api.interpret;

import ng.speakahouse.api.config.AppProperties;
import ng.speakahouse.api.session.DesignSessionEntity;
import ng.speakahouse.api.session.DesignSessionRepository;
import ng.speakahouse.api.spec.HouseSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterpretService {

    private static final Logger log = LoggerFactory.getLogger(InterpretService.class);

    private final AppProperties props;
    private final RuleBasedInterpreter ruleInterpreter;
    private final AnthropicInterpreter anthropicInterpreter;
    private final DesignSessionRepository sessionRepo;

    public InterpretService(
            AppProperties props,
            RuleBasedInterpreter ruleInterpreter,
            AnthropicInterpreter anthropicInterpreter,
            DesignSessionRepository sessionRepo) {
        this.props = props;
        this.ruleInterpreter = ruleInterpreter;
        this.anthropicInterpreter = anthropicInterpreter;
        this.sessionRepo = sessionRepo;
    }

    @Transactional
    public InterpretResult interpret(DesignSessionEntity session, String text, String baseVersionId, HouseSpec baseSpec) {
        // Deterministic safety & ambiguity checks always run BEFORE AI
        NigerianLexicon.LexResult lex = NigerianLexicon.parse(text);
        if (lex.injection() || lex.outOfScope() || !lex.clarify().isEmpty()
                || (baseSpec != null && lex.makeCheaper())) {
            return ruleInterpreter.interpret(text, baseVersionId, baseSpec);
        }

        if (props.ai().enabled() && session.getAiCalls() < props.ai().maxCallsPerSession()) {
            session.setAiCalls(session.getAiCalls() + 1);
            sessionRepo.save(session);
            try {
                return anthropicInterpreter.interpret(text, baseVersionId, baseSpec);
            } catch (Exception ex) {
                log.warn("Anthropic AI failed ({}), falling back to RuleBasedInterpreter", ex.getMessage());
            }
        }
        return ruleInterpreter.interpret(text, baseVersionId, baseSpec);
    }
}
