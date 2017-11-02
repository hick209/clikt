package com.github.ajalt.clikt.parser

import com.github.ajalt.clikt.options.ArgumentParser
import com.github.ajalt.clikt.options.OptionParser
import com.github.ajalt.clikt.options.ParamType
import com.github.ajalt.clikt.parser.HelpFormatter.ParameterHelp
import kotlin.reflect.KParameter

interface Parameter {
    /**
     * Process the parsed values and return the value to call the command with.
     *
     * This is called before the command is executed, and is called on parameters in the order that
     * they are defined on the command.
     *
     * If [exposeValue] is true, then [values] will contain all the parsed values from the current
     * command line. For options, this will be one value per time the option is given in argv. If
     * the option is never given, the list will be empty. For arguments, the list will contain at
     * most one value, since all values for a parameter are parsed at once. For other parameters
     * that don't parse argv but still contain a value, this function should return that value
     * directly.
     *
     * If [exposeValue] is false, then this function is still called, but its return value is
     * ignored. This is mostly useful for synthetic parameters like `--help`, which abort parsing
     * when called.
     *
     * @param context The Context for the current command
     * @param values A list of all values parsed from the command by this parameter.
     */
    fun processValues(context: Context, values: List<*>): Any?

    /**
     * Return true if this parameter provides a value to the called command.
     *
     * Most parameters will return true, although some, like `--help`, will return false.
     */
    val exposeValue: Boolean

    /**
     * Return information about this parameter that will be used by a [HelpFormatter].
     *
     * @return The help info for this parameter, or `null` to exlude this parameter from the
     *     displayed help.
     */
    val parameterHelp: ParameterHelp?

    /**
     * Return whether this parameter should process its values before non-eager parameters.
     *
     * All parameters that return true for this property will have their [processValues] called
     * before parameters that return false.
     */
    val eager: Boolean get() = false
}

abstract class ParsedParameter(val required: Boolean,
                               val metavar: String?,
                               val help: String,
                               override val exposeValue: Boolean) : Parameter

open class Option(val names: List<String>,
                  protected val parser: OptionParser, // TODO: move parsing to this class?
                  required: Boolean,
                  protected val default: Any?,
                  metavar: String?,
                  help: String,
                  exposeValue: Boolean=true) :
        ParsedParameter(required, metavar, help, exposeValue), OptionParser by parser {
    init {
        require(names.isNotEmpty()) // TODO messages
        for (name in names) {
            require(name.startsWith("-"))
        }
    }

    override fun processValues(context: Context, values: List<*>): Any? {
        // TODO nargs
        if (required && values.isEmpty()) throw MissingParameter("option", names)
        return values.lastOrNull() ?: default
    }

    override val parameterHelp: ParameterHelp
        get() = ParameterHelp(names, metavar,
                help,
                ParameterHelp.SECTION_OPTIONS,
                required, false) // TODO: repeatable
}

open class Argument<out T : Any>(final override val name: String,
                                 final override val nargs: Int,
                                 required: Boolean,
                                 protected val default: T?,
                                 metavar: String?,
                                 protected val type: ParamType<T>,
                                 help: String) :
        ParsedParameter(required, metavar, help, true), ArgumentParser {
    init {
        require(nargs != 0)
    }

    override fun processValues(context: Context, values: List<*>) =
            values.firstOrNull() ?: default

    override fun parse(args: List<String>): Any? {
        if (nargs == 1 && !required && args.isEmpty()) return null
        return if (nargs == 1) type.convert(args[0]) else args.map { type.convert(it) }
    }

    override val parameterHelp: ParameterHelp
        get() = ParameterHelp(listOf(name), metavar, help,
                ParameterHelp.SECTION_ARGUMENTS, required && nargs == 1 || nargs > 1, nargs < 0)
}


@Suppress("AddVarianceModifier")
abstract class ParameterFactory<T : Annotation> {
    abstract fun create(anno: T, funcParam: KParameter): Parameter

    @Suppress("UNCHECKED_CAST")
    internal fun createErased(anno: Annotation, funcParam: KParameter): Parameter {
        return create(anno as T, funcParam)
    }
}

