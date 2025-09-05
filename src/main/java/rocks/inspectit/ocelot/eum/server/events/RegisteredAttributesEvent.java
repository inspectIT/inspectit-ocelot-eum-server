package rocks.inspectit.ocelot.eum.server.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Event that is sent when new attributes are registered. This usually happens when new metric views are registered.
 */
public class RegisteredAttributesEvent extends ApplicationEvent {

    @Getter
    private final Set<String> registeredAttributes;

    /**
     * Create a new ApplicationEvent.
     *
     * @param source the object on which the event initially occurred (never {@code null})
     */
    public RegisteredAttributesEvent(Object source, Set<String> registeredAttributes) {
        super(source);
        this.registeredAttributes = Collections.unmodifiableSet(new HashSet<>(registeredAttributes));
    }
}
