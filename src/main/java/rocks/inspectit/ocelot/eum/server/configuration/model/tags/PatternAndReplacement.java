package rocks.inspectit.ocelot.eum.server.configuration.model.tags;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PatternAndReplacement {

    String pattern;

    String replacement;

    /**
     * Decides the behaviour if zero matches for the given pattern are found.
     * If this is false, no value will be provided for teh target tag.
     * If this is true, the previous value will be used without any replacement action.
     */
    @Builder.Default
    boolean keepNoMatch = true;
}
