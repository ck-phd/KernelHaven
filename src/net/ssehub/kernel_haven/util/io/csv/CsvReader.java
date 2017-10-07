package net.ssehub.kernel_haven.util.io.csv;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

import net.ssehub.kernel_haven.util.io.ITableReader;

/**
 * A reader for reading CSV files.
 *
 * @author Adam
 */
public class CsvReader implements ITableReader {

    private InputStreamReader in;
    
    private char separator;
    
    private Integer peeked;
    
    private boolean isEnd;
    
    /**
     * Creates a new {@link CsvReader} for the given input stream. Uses {@link CsvWriter#DEFAULT_SEPARATOR}.
     * 
     * @param in The input stream to read the CSV data from.
     */
    public CsvReader(InputStream in) {
        this(in, CsvWriter.DEFAULT_SEPARATOR);
    }
    
    /**
     * Creates a new {@link CsvReader} for the given input stream.
     * 
     * @param in The input stream to read the CSV data from.
     * @param separator The separator character to use.
     */
    public CsvReader(InputStream in, char separator) {
        this.in = new InputStreamReader(in, Charset.forName("UTF-8"));
        this.separator = separator;
    }
    
    @Override
    public void close() throws IOException {
        in.close();
    }
    
    /**
     * Reads the next element from the stream. This method must be used, so that {@link #peek()} correctly functions.
     * If the end of stream is detected, this method sets {@link #isEnd} to true.
     * 
     * @return The read character. -1 if end of stream is reached.
     * 
     * @throws IOException If reading the stream fails.
     */
    private int read() throws IOException {
        int result;
        if (peeked != null) {
            result = peeked;
            peeked = null;
        } else {
            result = in.read();
        }
        if (result == -1) {
            isEnd = true;
        }
        return result;
    }
    
    /**
     * Peeks at the next character. The next {@link #read()} call will return the same character. {@link #read()} must
     * be called at least once before calling {@link #peek()} again.
     * 
     * @return The next character that will be read. -1 if next character will be end of stream.
     * 
     * @throws IOException If reading the stream fails.
     * @throws IllegalStateException If {@link #peek()} is called twice, without {@link #read()} in between.
     */
    private int peek() throws IOException {
        if (peeked != null) {
            throw new IllegalStateException("Cannot peek while already storing peeked character");
        }
        peeked = in.read();
        return peeked;
    }
    
    /**
     * Removes the escaping " from a given field. The same string is returned, if field is not escaped.
     * 
     * @param field The field to un-escape.
     * @return The un-escaped field.
     */
    private String unescape(String field) {
        StringBuilder escaped = new StringBuilder();
        
        if (field.isEmpty() || field.charAt(0) != '"') {
            escaped.append(field);
        } else {
            for (int i = 1; i < field.length(); i++) {
                char c = field.charAt(i);
                
                if (c == '"' && i == field.length() - 1) {
                    // trailing " means escaped sequence ended
                    break;
                } else if (c == '"' && field.charAt(i + 1) == '"') {
                    // double "" mean insert one "
                    // move i to next character, so that only one " is added
                    i++;
                }
                
                escaped.append(c);
            }
        }
        
        return escaped.toString();
    }
    
    /**
     * Reads and parses a single line of CSV data. Splits at separator character. Considers (an un-escapes)
     * escaped values.
     * 
     * @return The fields found in the CSV.
     * 
     * @throws IOException If reading the stream fails.
     */
    private String[] readLine() throws IOException {
        List<String> parts = new LinkedList<>();
        
        // whether we are currently inside an escape sequence
        // an escaped sequence starts with a " and ends with a "
        // the start " must be the first character of the field
        // the end " must be the last character of a field
        boolean inEscaped = false;
        
        // contains characters of the current field
        // new characters are added until a (unescaped) separator is found
        // when a (unescaped) separator is found, the contents of this contain the previous field
        StringBuilder currentElement = new StringBuilder();
        
        // break; will be called once the one of line (or stream) is reached
        while (true) {
            char c = (char) read();
            if (isEnd) {
                break;
            }
            
            if (c == separator && !inEscaped) {
                // we found an unescaped separator
                parts.add(currentElement.toString());
                currentElement.setLength(0);
                // jump back to start, to not add the separator to the next field
                continue;
                
            } else if (c == '"') {
                if (!inEscaped && currentElement.length() == 0) {
                    // we found a " at the beginning of a field -> the field is escaped
                    inEscaped = true;
                    
                } else if (inEscaped) {
                    // check if we are at the end of a field, by peeking at the next character
                    int peek = peek();
                    if (peek == -1 || peek == separator || peek == '\n') {
                        // we found a " at the end of a field -> escaping ended
                        inEscaped = false;
                        // the next iteration will read() the end of field, so we don't have to do anything
                    }
                }
                
            } else if (c == '\n' && !inEscaped) {
                // a non-escaped end of line -> we are done with this row
                break;
            }
            currentElement.append(c);
        }
        
        String[] result;
        if (parts.isEmpty() && currentElement.length() == 0 && peek() == -1) {
            // ignore last line in file, if its empty
            // we know that we are at the last line, if we didn't find any fields
            //   (parts.empty() && currentElement.empty()) and the next char will be the end of stream
            result = null;
            isEnd = true;
            
        } else {
            // add the last field (which we didn't find a separator for, because it was ended with a \n)
            parts.add(currentElement.toString());
            
            result = new String[parts.size()];
            int i = 0;
            for (String part : parts) {
                result[i++] = unescape(part);
            }
        }
        return result;
    }
    
    @Override
    public String[] readNextRow() throws IOException {
        String[] result = null;
        
        if (!isEnd) {
            result = readLine();
        }
        
        return result;
    }

}
