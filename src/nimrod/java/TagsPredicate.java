package nimrod.java;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import org.hsqldb.lib.HashSet;

public class TagsPredicate {

    private static final SmileFactory FACTORY = new SmileFactory();

    public static boolean contains(byte[] metric, String tags) throws Exception {
        JsonParser parser = FACTORY.createJsonParser(metric);
        try {
            HashSet candidates = new HashSet();
            boolean contains = true;
            candidates.addAll(tags.split(","));
            while (parser.nextToken() != null) {
                if (parser.getCurrentName() != null && parser.getCurrentName().equals("tags")) {
                    HashSet actual = new HashSet();
                    while (!parser.nextToken().equals(JsonToken.END_ARRAY)) {
                        actual.add(parser.getText());
                    }
                    contains = actual.isEmpty() || actual.containsAll(candidates);
                    break;
                }
            }
            return contains;
        } finally {
            parser.close();
        }
    }
}
