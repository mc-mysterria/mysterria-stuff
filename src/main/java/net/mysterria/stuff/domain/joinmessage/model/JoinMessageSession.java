package net.mysterria.stuff.domain.joinmessage.model;

import lombok.Getter;
import lombok.Setter;
import net.mysterria.stuff.domain.joinmessage.model.source.SessionStep;

@Getter
@Setter
public class JoinMessageSession {
    private SessionStep step = SessionStep.AWAITING_JOIN;
    private String joinMessage;
    private String quitMessage;
}
