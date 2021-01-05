/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.search.macros;

import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import parsii.tokenizer.Position;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.pasta.noodle.Environment;
import sirius.pasta.noodle.compiler.CompilationContext;
import sirius.pasta.noodle.macros.BasicMacro;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;

/**
 * Wraps the static methods needed to produce XContent and output as String
 */
@Register
public class ToXContentMacro extends BasicMacro {
    @Override
    public Class<?> getType() {
        return String.class;
    }

    @Override
    protected void verifyArguments(CompilationContext compilationContext, Position position, List<Class<?>> args) {
        if ((args.size() != 1) || !(ToXContent.class.isAssignableFrom(args.get(0)))) {
            throw new IllegalArgumentException("One parameter that must implement ToXContent is expected");
        }
    }

    @Override
    public Object invoke(Environment environment, Object[] args) {
        try (XContentBuilder builder = XContentFactory.jsonBuilder().prettyPrint()) {
            return ((ToXContent) args[0]).toXContent(builder, ToXContent.EMPTY_PARAMS).string();
        } catch (IOException e) {
            Exceptions.handle(e);
        }
        return "";
    }

    @Nonnull
    @Override
    public String getName() {
        return "toXContent";
    }

    @Override
    public String getDescription() {
        return "Calls the toXContent method of the given parameter and outputs it as String.";
    }
}
