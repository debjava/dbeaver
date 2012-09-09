/*
 * Copyright (C) 2010-2012 Serge Rieder
 * serge@jkiss.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.jkiss.utils;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Common utils
 */
public class CommonUtils {
    //static final Log log = LogFactory.getLog(CommonUtils.class);
    private static Object[] EMPTY_ARRAY = new Object[0];

	public static boolean isJavaIdentifier(CharSequence str)
	{
		for (int i = 0; i < str.length(); i++) {
			if (!Character.isJavaIdentifierPart(str.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	public static String escapeJavaString(String str) {
		if (str.indexOf('"') == -1 && str.indexOf('\n') == -1) {
			return str;
		}
		StringBuilder res = new StringBuilder(str.length() + 5);
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			switch (c) {
				case '"':
					res.append("\\\"");
					break;
				case '\n':
					res.append("\\n");
					break;
				case '\r':
					break;
				default:
					res.append(c);
					break;
			}
		}
		return res.toString();
	}

	public static String escapeIdentifier(String str) {
		if (str == null) {
			return null;
		}
		StringBuilder res = new StringBuilder(str.length());
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if (Character.isJavaIdentifierPart(c)) {
				res.append(c);
			} else {
                if (res.length() == 0 || res.charAt(res.length() - 1) != '_') {
				    res.append('_');
                }
			}
		}
		return res.toString();
	}

    public static String escapeFileName(String str) {
        if (str == null) {
            return null;
        }
        StringBuilder res = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isISOControl(c)
                || c == '\\'
                || c == '/'
                || c == '<'
                || c == '>'
                || c == '|'
                || c == '"'
                || c == ':'
                || c == '*'
                || c == '?')
            {
                res.append('_');
            } else {
                res.append(c);
            }
        }
        return res.toString();
    }

    public static String makeDirectoryName(String str)
    {
        if (!str.endsWith("/")) {
            str += "/";
        }
        return str;
    }

    public static String removeTrailingSlash(String str)
    {
        while (str.endsWith("/") || str.endsWith("\\")) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

	public static String capitalizeWord(String str) {
		if (isEmpty(str) || Character.isUpperCase(str.charAt(0))) {
			return str;
		}
		return Character.toUpperCase(str.charAt(0)) + str.substring(1);
	}

	public static boolean isEmpty(CharSequence value)
	{
		return value == null || value.length() == 0;
	}

	public static boolean isEmpty(Object[] arr)
	{
		return arr == null || arr.length == 0;
	}

	public static boolean isEmpty(Collection<?> value)
	{
		return value == null || value.isEmpty();
	}

    public static boolean isEmpty(Map<?,?> value)
    {
        return value == null || value.isEmpty();
    }

    public static <T> Collection<T> safeCollection(Collection<T> theList)
    {
        if (theList == null) {
            theList = Collections.emptyList();
        }
        return theList;
    }

    public static <T> List<T> safeList(List<T> theList)
    {
        if (theList == null) {
            theList = Collections.emptyList();
        }
        return theList;
    }

    public static <T> List<T> copyList(Collection<T> theList)
    {
        if (theList == null) {
            return new ArrayList<T>();
        } else {
            return new ArrayList<T>(theList);
        }
    }

	public static String getString(String value)
	{
		return value == null ? "" : value;
	}

    public static boolean getBoolean(String value)
    {
        return Boolean.parseBoolean(value);
    }

    public static boolean getBoolean(String value, boolean defaultValue)
    {
        return isEmpty(value) ? defaultValue : Boolean.parseBoolean(value);
    }

    public static boolean getBoolean(Object value, boolean defaultValue)
    {
        return value != null && (value instanceof Boolean ? (Boolean) value : getBoolean(value.toString()));
    }

    public static String getLineSeparator()
    {
        String lineSeparator = System.getProperty("line.separator");
        return lineSeparator == null ? "\n" : lineSeparator;
    }

    public static Throwable getRootCause(Throwable ex)
	{
		Throwable rootCause = ex;
		for (; ;) {
			if (rootCause.getCause() != null) {
				rootCause = rootCause.getCause();
			} else if (rootCause instanceof InvocationTargetException && ((InvocationTargetException) rootCause).getTargetException() != null) {
				rootCause = ((InvocationTargetException) rootCause).getTargetException();
			} else {
				break;
			}
		}
		return rootCause;
	}

	public static String getShortClassName(Class clas)
	{
		String name = clas.getName();
		int divPos = name.lastIndexOf('.');
		return divPos == -1 ? name : name.substring(divPos + 1);
	}

	public static String formatSentence(String sent)
	{
		if (sent == null) {
			return "";
		}
		StringBuilder result = new StringBuilder();
		StringTokenizer st = new StringTokenizer(sent, " \t\n\r-,.\\/", true);
		while (st.hasMoreTokens()) {
			String word = st.nextToken();
			if (word.length() > 0) {
				result.append(formatWord(word));
			}
		}

		return result.toString();
	}

	public static String formatWord(String word)
	{
		if (word == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(word.length());
		sb.append(Character.toUpperCase(word.charAt(0)));
		for (int i = 1; i < word.length(); i++) {
			char c = word.charAt(i);
			if ((c == 'i' || c == 'I') && sb.charAt(i - 1) == 'I') {
				sb.append('I');
			} else {
				sb.append(Character.toLowerCase(c));
			}
		}
		return sb.toString();
	}

	public static boolean isEmpty(short[] array)
	{
		return array == null || array.length == 0;
	}

	public static boolean contains(short[] array, short value)
	{
		if (isEmpty(array)) return false;
		for (short v : array) {
			if (v == value) return true;
		}
		return false;
	}

	public static boolean isEmpty(int[] array)
	{
		return array == null || array.length == 0;
	}

	public static boolean contains(int[] array, int value)
	{
		if (isEmpty(array)) return false;
		for (int v : array) {
			if (v == value) return true;
		}
		return false;
	}

	public static boolean isEmpty(long[] array)
	{
		return array == null || array.length == 0;
	}

	public static boolean contains(long[] array, long value)
	{
		if (isEmpty(array)) return false;
		for (long v : array) {
			if (v == value) return true;
		}
		return false;
	}

    public static <OBJECT_TYPE> boolean contains(OBJECT_TYPE[] array, OBJECT_TYPE value)
    {
        if (isEmpty(array)) return false;
        for (OBJECT_TYPE v : array) {
            if (equalObjects(value, v)) return true;
        }
        return false;
    }

	public static boolean equalObjects(Object o1, Object o2)
	{
		if (o1 == o2) {
			return true;
		}
		if (o1 == null || o2 == null) {
			return false;
		}
		return o1.equals(o2);
	}

    public static String toString(Object object)
    {
        if (object == null) {
            return "";
        } else if (object instanceof String) {
            return (String)object;
        } else {
            return object.toString();
        }
    }

    public static int toInt(Object object)
    {
        if (object == null) {
            return 0;
        } else if (object instanceof Number) {
            return ((Number)object).intValue();
        } else {
            try {
                return Integer.parseInt(toString(object));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }

    public static boolean isInt(Object object)
    {
        if (object == null) {
            return false;
        } else if (object instanceof Number) {
            return true;
        } else {
            try {
                Integer.parseInt(toString(object));
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    public static long toLong(Object object)
    {
        if (object == null) {
            return 0;
        } else if (object instanceof Number) {
            return ((Number)object).longValue();
        } else {
            try {
                return Long.parseLong(toString(object));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }

    public static boolean isLong(Object object)
    {
        if (object == null) {
            return false;
        } else if (object instanceof Number) {
            return true;
        } else {
            try {
                Long.parseLong(toString(object));
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    public static String toHexString(byte[] bytes)
    {
        return bytes == null ? "" : toHexString(bytes, 0, bytes.length);
    }

    public static String toHexString(byte[] bytes, int offset, int length)
    {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder buffer = new StringBuilder(length * 2 + 2);
        buffer.append("0x");
        for (int i = offset; i < offset + length && i < bytes.length; i++) {
            if (bytes[i] < 16) buffer.append('0');
            buffer.append(Integer.toHexString(bytes[i]));
        }
        return buffer.toString().toUpperCase();
    }

    public static List<String> splitString(String str, char delimiter)
    {
        if (CommonUtils.isEmpty(str)) {
            return Collections.emptyList();
        } else {
            List<String> result = new ArrayList<String>();
            StringTokenizer st = new StringTokenizer(str, String.valueOf(delimiter));
            while (st.hasMoreTokens()) {
                result.add(st.nextToken());
            }
            return result;
        }
    }

    public static String makeString(List<String> tokens, char delimiter)
    {
        if (isEmpty(tokens)) {
            return "";
        } else if (tokens.size() == 1) {
            return tokens.get(0);
        } else {
            StringBuilder buf = new StringBuilder();
            for (String token : tokens) {
                if (buf.length() > 0) {
                    buf.append(delimiter);
                }
                buf.append(token);
            }
            return buf.toString();
        }
    }

    public static String truncateString(String query, int maxLength)
    {
        if (query.length() > maxLength) {
            return query.substring(0, maxLength);
        }
        return query;
    }

    public static boolean isEmptyTrimmed(String str)
    {
        return str == null || str.length() == 0 || str.trim().length() == 0;
    }

    public static <T extends Enum> T valueOf(Class<T> type, String name)
    {
        return valueOf(type, name, false);
    }

    public static <T extends Enum> T valueOf(Class<T> type, String name, boolean underscoreSpaces)
    {
        if (isEmpty(name)) {
            return null;
        }
        if (underscoreSpaces) {
            name = name.replace(' ', '_');
        }
        try {
            return (T) Enum.valueOf(type, name);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static <T> T[] safeArray(T[] array) {
        if (array == null) {
            return (T[]) EMPTY_ARRAY;
        } else {
            return array;
        }
    }

}
