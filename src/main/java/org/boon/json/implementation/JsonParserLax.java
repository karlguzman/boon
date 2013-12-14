package org.boon.json.implementation;

import org.boon.Dates;
import org.boon.core.reflection.Reflection;
import org.boon.json.JsonException;
import org.boon.json.JsonParser;
import org.boon.primitive.CharBuf;
import org.boon.primitive.CharScanner;
import org.boon.primitive.Chr;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.*;


import static org.boon.Exceptions.die;
import static org.boon.primitive.CharScanner.isInteger;
import static org.boon.primitive.CharScanner.parseInt;
import static org.boon.primitive.CharScanner.parseLong;

/**
 * Created by rick on 12/12/13.
 */
public class JsonParserLax implements JsonParser {


    private char[] charArray;
    private int __index;
    private char __currentChar;


    boolean inList;
    boolean inObject;

    private  final char OBJECT_ITEM_DELIMETER_TOKEN;

    private  final char START_ARRAY_TOKEN;


    private  final char END_ARRAY_TOKEN;


    private  final char KEY_ASSIGNMENT_OPERATOR;

    private  static final boolean internKeys = Boolean.parseBoolean ( System.getProperty ( "org.boon.json.implementation.internKeys", "true" ) );


    public JsonParserLax () {
        OBJECT_ITEM_DELIMETER_TOKEN = ',';
        START_ARRAY_TOKEN = '[';
        END_ARRAY_TOKEN = ']';
        KEY_ASSIGNMENT_OPERATOR = ':';

    }


    public JsonParserLax (boolean plist) {

        if (plist) {
            OBJECT_ITEM_DELIMETER_TOKEN = ';';
            START_ARRAY_TOKEN = '(';
            END_ARRAY_TOKEN = ')';

            KEY_ASSIGNMENT_OPERATOR = '=';

        } else {
            OBJECT_ITEM_DELIMETER_TOKEN = ',';
            START_ARRAY_TOKEN = '[';
            END_ARRAY_TOKEN = ']';
            KEY_ASSIGNMENT_OPERATOR = ':';
        }


    }


    @SuppressWarnings ( "unchecked" )
    public Object decode ( char[] cs ) {
        __index = 0;
        charArray = cs;
        return decodeValue ();
    }


    public Object decode ( String cs ) {
        __index = 0;
        this.charArray = Reflection.toCharArray ( cs );
        return decodeValue ();
    }


    public Object decode ( byte[] bytes ) {
        __index = 0;
        this.charArray = Reflection.toCharArray ( bytes );
        return decodeValue ();
    }


    private final boolean hasMore () {
        return __index + 1 < charArray.length;
    }

    private final char nextChar () {

        try {
            if ( hasMore () ) {
                __index++;
                return __currentChar = charArray[ __index ];
            } else {
                return '\u0000';
            }
        } catch ( Exception ex ) {
            throw new JsonException ( exceptionDetails ( "unable to advance character" ), ex );
        }
    }


    private String exceptionDetails ( String message ) {
        CharBuf buf = CharBuf.create ( 255 );

        buf.addLine ( message );


        buf.addLine ( "" );
        buf.addLine ( "The current character read is " + charDescription ( __currentChar ) );


        buf.addLine ( message );

        int line = 0;
        int lastLineIndex = 0;

        for ( int i = 0; i < __index && i < charArray.length; i++ ) {
            if ( charArray[ i ] == '\n' ) {
                line++;
                lastLineIndex = i + 1;
            }
        }

        int count = 0;

        for ( int i = lastLineIndex; i < charArray.length; i++, count++ ) {
            if ( charArray[ i ] == '\n' ) {
                break;
            }
        }


        buf.addLine ( "line number " + line + 1 );
        buf.addLine ( "index number " + __index );


        try {
            buf.addLine ( new String ( charArray, lastLineIndex, count ) );
        } catch ( Exception ex ) {

            try {
                int index = ( __index - 10 < 0 ) ? 0 : __index - 10;

                buf.addLine ( new String ( charArray, index, __index ) );
            } catch ( Exception ex2 ) {
                buf.addLine ( new String ( charArray, 0, charArray.length ) );
            }
        }
        for ( int i = 0; i < ( __index - lastLineIndex - 1 ); i++ ) {
            buf.add ( '.' );
        }
        buf.add ( '^' );

        return buf.toString ();
    }

    private void skipWhiteSpace () {


        label:
        for (; __index < this.charArray.length; __index++ ) {
            __currentChar = charArray[ __index ];
            switch ( __currentChar ) {
                case '\n':
                    continue label;

                case '\r':
                    continue label;

                case ' ':
                    continue label;

                case '\t':
                    continue label;

                default:
                    break label;

            }
        }

    }

    private Object decodeJsonObjectLax () {

        if ( __currentChar == '{' )
            this.nextChar ();

        Map<String, Object> map = new LinkedHashMap<> ();


        inObject = true;

        boolean foundKeyQuote = false;

        skipWhiteSpace ();
        int startIndexOfKey = __index;

        done:
        for (; __index < this.charArray.length; __index++ ) {

            skipWhiteSpace ();

            switch ( __currentChar ) {
                case '/': /* */ //
                    handleComment ();
                    startIndexOfKey = __index;
                    break;

                case '#':
                    handleBashComment ();
                    startIndexOfKey = __index;
                    break;

                case '=':
                case ':':
                    if (__currentChar != KEY_ASSIGNMENT_OPERATOR) {
                        continue;
                    }
                    if ( !foundKeyQuote ) {

                        char startChar = charArray[ startIndexOfKey ];

                        if ( startChar == OBJECT_ITEM_DELIMETER_TOKEN) {
                                startIndexOfKey++;
                        }

                        char[] chars = Chr.trim ( charArray, startIndexOfKey, __index );
                        String key = new String ( chars );
                        if (internKeys) {
                            key = key.intern ();
                        }

                        __index++; //skip :

                        Object value = decodeValue ();
                        skipWhiteSpace ();
                        map.put ( key, value );
                        startIndexOfKey = __index;
                        if ( __currentChar == '}' ) {
                            __index++;
                            break done;
                        }

                        break;
                    }

                case '\'':
                case '"':
                    foundKeyQuote = true;
                    String key = (String) decodeString ( __currentChar );

                    skipWhiteSpace ();

                    if ( __currentChar != KEY_ASSIGNMENT_OPERATOR ) {

                        complain ( "expecting current character to be : but got " + charDescription ( __currentChar ) + "\n" );
                    }
                    __index++;
                    Object value = decodeValue ();

                    skipWhiteSpace ();
                    map.put ( key, value );
                    startIndexOfKey = __index;
                    if ( __currentChar == '}' ) {
                        __index++;
                        break done;
                    }
                    break;


                case '}':
                    __index++;
                    if (hasMore () && OBJECT_ITEM_DELIMETER_TOKEN == ';') {
                        if (charArray[__index] == ';') {
                            __index++;
                        }
                    }
                    break done;

            }
        }


        inObject = false;
        return map;
    }

    private void complain ( String complaint ) {
        throw new JsonException ( exceptionDetails ( complaint ) );
    }


    private Object decodeValue () {
        Object value = null;


        for (; __index < charArray.length; __index++ ) {
            skipWhiteSpace ();


            switch ( __currentChar ) {
                case '\n':
                    break;

                case '\r':
                    break;

                case ' ':
                    break;

                case '\t':
                    break;

                case '\b':
                    break;

                case '\f':
                    break;

                case '/': /* */ //
                    handleComment ();
                    break;

                case '#':
                    handleBashComment ();
                    break;

                case '"':
                    value = decodeString ( '"' );
                    break;

                case '\'':
                    value = decodeString ( '\'' );
                    break;


                case 't':
                    if ( isTrue () ) {
                        value = decodeTrue ();
                    } else {
                        value = decodeStringLax ();
                    }
                    break;

                case 'f':
                    if ( isFalse () ) {
                        value = decodeFalse ();
                    } else {
                        value = decodeStringLax ();
                    }
                    break;

                case 'n':
                    if ( isNull () ) {
                        value = decodeNull ();
                        return value;
                    } else {
                        value = decodeStringLax ();
                    }

                    break;

                case '(':
                case '[':
                    value = decodeJsonArray ();
                    break;

                case '{':
                    value = decodeJsonObjectLax ();
                    break;

                case '1':
                    value = decodeNumber ();
                    break;

                case '2':
                    value = decodeNumber ();
                    break;

                case '3':
                    value = decodeNumber ();
                    break;

                case '4':
                    value = decodeNumber ();
                    break;

                case '5':
                    value = decodeNumber ();
                    break;

                case '6':
                    value = decodeNumber ();
                    break;

                case '7':
                    value = decodeNumber ();
                    break;

                case '8':
                    value = decodeNumber ();
                    break;

                case '9':
                    value = decodeNumber ();
                    break;

                case '0':
                    value = decodeNumber ();
                    break;

                case '-':
                    value = decodeNumber ();
                    break;

                default:
                    value = decodeStringLax ();

            }

            if ( value != null ) {
                return value;
            }
        }

        return null;
    }

    private void handleBashComment () {
        for (; __index < charArray.length; __index++ ) {
            __currentChar = charArray[ __index ];

            if ( __currentChar == '\n' ) {
                return;
            }
        }
    }

    private void handleComment () {


        if ( hasMore () ) {

            __index++;
            __currentChar = charArray[ __index ];

            switch ( __currentChar ) {
                case '*':
                    for (; __index < charArray.length; __index++ ) {
                        __currentChar = charArray[ __index ];

                        if ( __currentChar == '*' ) {
                            if ( hasMore () ) {
                                __index++;
                                __currentChar = charArray[ __index ];
                                if ( __currentChar == '/' ) {
                                    if ( hasMore () ) {
                                        __index++;
                                        return;
                                    }
                                }
                            } else {
                                complain ( "missing close of comment" );
                            }
                        }


                    }

                case '/':
                    for (; __index < charArray.length; __index++ ) {
                        __currentChar = charArray[ __index ];

                        if ( __currentChar == '\n' ) {
                            return;
                        }
                    }
            }

        }


    }

    private Object decodeNumber () {


        boolean doubleFloat = false;

        boolean minus = false;

        boolean simple = true;

        int sign = 1;

        int index;

        int digitsPastPoint = 0;


        __currentChar = charArray[ __index ];

        if ( __currentChar == '-' ) {
            minus = true;
            __index++;
            sign = -1;
        }

        int startIndex = __index;


        loop:
        for ( index = __index; index < charArray.length; index++ ) {
            __currentChar = charArray[ index ];

            if ( doubleFloat ) {
                digitsPastPoint++;
            }


            switch ( __currentChar ) {
                case ' ':
                    __index = index + 1;
                    break loop;

                case '\t':
                    __index = index + 1;
                    break loop;

                case '\n':
                    __index = index + 1;
                    break loop;

                case '\r':
                    __index = index + 1;
                    break loop;

                case ';':
                case ',':
                    if (__currentChar==OBJECT_ITEM_DELIMETER_TOKEN){
                        break loop;
                    } else {
                        complain ( "unexpected token " + __currentChar );
                    }

                case ')':
                case ']':
                    if (__currentChar==END_ARRAY_TOKEN){
                        break loop;
                    } else {
                        complain ( "unexpected token " + __currentChar );
                    }

                case '}':
                    break loop;

                case '1':
                    continue loop;

                case '2':
                    continue loop;

                case '3':
                    continue loop;

                case '4':
                    continue loop;

                case '5':
                    continue loop;

                case '6':
                    continue loop;

                case '7':
                    continue loop;

                case '8':
                    continue loop;

                case '9':
                    continue loop;

                case '0':
                    continue loop;

                case '-':
                    continue loop;


                case '+':

                    simple = false;
                    doubleFloat = true;
                    continue loop;

                case 'e':
                    simple = false;
                    doubleFloat = true;
                    continue loop;

                case 'E':

                    simple = false;
                    doubleFloat = true;
                    continue loop;

                case '.':
                    doubleFloat = true;
                    continue loop;

            }

            complain ( "expecting number char but got current char " + charDescription ( __currentChar ) );
        }

        __index = index;


        Object value;
        if ( doubleFloat ) {
            value = CharScanner.simpleDouble ( this.charArray, simple, minus, digitsPastPoint - 1, startIndex, __index );
        } else {

            if ( isInteger ( this.charArray, startIndex, __index - startIndex, minus ) ) {
                value = parseInt ( charArray, startIndex, __index - startIndex ) * sign;
            } else {
                value = parseLong ( charArray, startIndex, __index - startIndex ) * sign;
            }

        }

        skipWhiteSpace ();

        return value;

    }


    private static char[] NULL = Chr.chars ( "null" );

    private Object decodeNull () {

        if ( __index + NULL.length <= charArray.length ) {
            if ( charArray[ __index ] == 'n' &&
                    charArray[ ++__index ] == 'u' &&
                    charArray[ ++__index ] == 'l' &&
                    charArray[ ++__index ] == 'l' ) {
                nextChar ();
                return null;
            }
        }
        throw new JsonException ( exceptionDetails ( "null not parse properly" ) );
    }

    private boolean isNull () {

        if ( __index + NULL.length <= charArray.length ) {
            if ( charArray[ __index ] == 'n' &&
                    charArray[ __index + 1 ] == 'u' &&
                    charArray[ __index + 2 ] == 'l' &&
                    charArray[ __index + 3 ] == 'l' ) {
                return true;
            }
        }
        return false;
    }

    private static char[] TRUE = Chr.chars ( "true" );

    private boolean decodeTrue () {

        if ( __index + TRUE.length <= charArray.length ) {
            if ( charArray[ __index ] == 't' &&
                    charArray[ ++__index ] == 'r' &&
                    charArray[ ++__index ] == 'u' &&
                    charArray[ ++__index ] == 'e' ) {

                nextChar ();
                return true;

            }
        }

        throw new JsonException ( exceptionDetails ( "true not parsed properly" ) );
    }


    private boolean isTrue () {

        if ( __index + TRUE.length <= charArray.length ) {
            if ( charArray[ __index ] == 't' &&
                    charArray[ __index + 1 ] == 'r' &&
                    charArray[ __index + 2 ] == 'u' &&
                    charArray[ __index + 3 ] == 'e' ) {
                return true;

            }
        }

        return false;
    }


    private static char[] FALSE = Chr.chars ( "false" );

    private boolean decodeFalse () {

        if ( __index + FALSE.length <= charArray.length ) {
            if ( charArray[ __index ] == 'f' &&
                    charArray[ ++__index ] == 'a' &&
                    charArray[ ++__index ] == 'l' &&
                    charArray[ ++__index ] == 's' &&
                    charArray[ ++__index ] == 'e' ) {
                nextChar ();
                return false;
            }
        }
        throw new JsonException ( exceptionDetails ( "false not parsed properly" ) );
    }

    private boolean isFalse () {

        if ( __index + FALSE.length <= charArray.length ) {
            if ( charArray[ __index ] == 'f' &&
                    charArray[ __index + 1 ] == 'a' &&
                    charArray[ __index + 2 ] == 'l' &&
                    charArray[ __index + 3 ] == 's' &&
                    charArray[ __index + 4 ] == 'e' ) {
                return true;
            }
        }
        return false;
    }

    private Object decodeStringLax () {

        __currentChar = charArray[ __index ];

        if ( __index < charArray.length && __currentChar == '"' ) {
            __index++;

        }


        final int startIndex = __index;


        boolean escape = false;
        boolean hasEscaped = false;
        boolean skip = false;


        done:
        for (; __index < this.charArray.length; __index++ ) {
            __currentChar = charArray[ __index ];
            switch ( __currentChar ) {

                case '"':
                    if ( !escape ) {
                        skip = true;
                        break done;
                    } else {
                        escape = false;
                        continue;
                    }

                case ']':
                case ')':
                    if ( inList && __currentChar == END_ARRAY_TOKEN) {
                        skip = false;
                        break done;
                    } else {
                        break;
                    }

                case '}':
                    if ( inObject ) {
                        skip = false;
                        break done;
                    } else {
                        break;
                    }


                case ';':
                case ',':
                    if (__currentChar == OBJECT_ITEM_DELIMETER_TOKEN || __currentChar == ',') {
                    if ( inObject || inList ) {
                        skip = false;
                        break done;
                    } else {
                        break;
                    }
                    } else {
                        complain ( "unexpected token " + __currentChar );
                    }

                case '\\':
                    hasEscaped = true;
                    escape = true;
                    continue;

            }
            escape = false;
        }


        Object value = null;
        if ( hasEscaped ) {
            final char[] chars = Chr.trim ( charArray, startIndex, __index );
            value = JsonStringDecoder.decodeForSure ( chars );
        } else {
            final char[] chars = Chr.trim ( charArray, startIndex, __index );
            value = new String ( chars );
        }

        if ( __index < charArray.length && skip ) {
            __index++;
        }

        return value;
    }


    private Object decodeString ( final char terminator ) {

        __currentChar = charArray[ __index ];

        if ( __index < charArray.length && __currentChar == terminator ) {
            __index++;

        }


        final int startIndex = __index;


        boolean escape = false;
        boolean hasEscaped = false;

        done:
        for (; __index < this.charArray.length; __index++ ) {
            __currentChar = charArray[ __index ];
            switch ( __currentChar ) {

                case '\'':
                case '"':
                    if ( terminator == __currentChar ) {
                        if ( !escape ) {
                            break done;
                        } else {
                            escape = false;
                            continue;
                        }
                    }


                case '\\':
                    hasEscaped = true;
                    escape = true;
                    continue;

            }
            escape = false;
        }

        Object value = null;
        if ( hasEscaped ) {
            value = JsonStringDecoder.decodeForSure ( charArray, startIndex, __index );
        } else {

            if (Dates.isISO8601QuickCheck ( charArray, startIndex, __index )) {
                value = Dates.fromISO8601DateLoose ( charArray, startIndex, __index );
            } else {

                value = new String ( charArray, startIndex, ( __index - startIndex ) );

            }

        }

        if ( __index < charArray.length ) {
            __index++;
        }

        return value;
    }

    private List decodeJsonArray () {
        if ( __currentChar == START_ARRAY_TOKEN ) {
            this.nextChar ();
        }


        inList = true;

        skipWhiteSpace ();

        List<Object> list = new ArrayList<> ();

        /* the list might be empty  */
        if ( __currentChar == END_ARRAY_TOKEN ) {
            this.nextChar ();
            inList = false;
            return list;
        }


        int arrayIndex = 0;

        skipWhiteSpace ();

        do {

            skipWhiteSpace ();

            Object arrayItem = decodeValue ();

            if ( arrayItem == null ) {
                list.add ( null ); //JSON null detected
            } else {
                list.add ( arrayItem );
            }

            arrayIndex++;


            skipWhiteSpace ();

            char c = __currentChar;

            if ( c == ',' ) {
                this.nextChar ();
                continue;
            } else if ( c == END_ARRAY_TOKEN ) {
                this.nextChar ();
                break;
            } else {

                String charString = charDescription ( c );

                complain (
                        String.format ( "expecting a ',' or a ']', " +
                                " but got \nthe current character of  %s " +
                                " on array index of %s \n", charString, arrayIndex )
                );

            }
        } while ( this.hasMore () );

        inList = false;
        return list;
    }

    private String charDescription ( char c ) {
        String charString;
        if ( c == ' ' ) {
            charString = "[SPACE]";
        } else if ( c == '\t' ) {
            charString = "[TAB]";

        } else if ( c == '\n' ) {
            charString = "[NEWLINE]";

        } else {
            charString = "'" + c + "'";
        }

        charString = charString + " with an int value of " + ( ( int ) c );
        return charString;
    }


    @Override
    public <T> T parse ( Class<T> type, String str ) {
        return ( T ) this.decode ( str );
    }

    @Override
    public <T> T parse ( Class<T> type, byte[] bytes ) {
        return ( T ) this.decode ( bytes );
    }

    @Override
    public <T> T parse ( Class<T> type, CharSequence charSequence ) {
        return parse ( type, charSequence.toString () );
    }

    @Override
    public <T> T parse ( Class<T> type, char[] chars ) {
        return ( T ) this.decode ( chars );
    }

    @Override
    public <T> T parse ( Class<T> type, Reader reader ) {

        die ( "you are using the wrong class" );
        return null;
    }

    @Override
    public <T> T parse ( Class<T> type, InputStream input ) {
        die ( "you are using the wrong class" );
        return null;
    }

    @Override
    public <T> T parse ( Class<T> type, InputStream input, Charset charset ) {
        die ( "you are using the wrong class" );
        return null;
    }

}