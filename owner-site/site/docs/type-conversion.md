---
layout: docs
title: Type conversion
prev_section: parametrized-properties
next_section: variables-expansion
permalink: /docs/type-conversion/
---

OWER API supports properties conversion for primitive types and enums.
When you define the *mapping interface* you can use a wide set of return types,
and they will be automatically converted from `String` to the primitive types
and enums:

```java
// conversion happens from the value specified in the
// properties files (if available).
int maxThreads();

// conversion happens also from @DefaultValue
@DefaultValue("3.1415")
double pi();

// enum values are case sensitive!
// java.util.concurrent.TimeUnit is an enum
@DefaultValue("NANOSECONDS");
TimeUnit timeUnit();
```

It is possible to have configuration interfaces to declare business objects as return types, many are compatible and
you can also define your own objects:

The easiest way is to define your business object with a public constructor
taking a single parameter of type `java.lang.String`:

```java
public class CustomType {
    private final String text;

    public CustomType(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}

public interface SpecialTypes extends Config {
    @DefaultValue("foobar.txt")
    File sampleFile();

    @DefaultValue("http://owner.aeonbits.org")
    URL sampleURL();

    @DefaultValue("example")
    CustomType customType();

    @DefaultValue("Hello %s!")
    CustomType salutation(String name);
}
```

OWNER API will take the value "example" and pass it to the CustomType
constructor then return it.

Arrays and Collections
----------------------

OWNER have first class support for Java Arrays and Collections.

So now you can define properties like:

```java
public class MyConfig extends Config {

  @DefaultValue("apple, pear, orange")
  public String[] fruit();

  @Separator(";")
  @DefaultValue("0; 1; 1; 2; 3; 5; 8; 13; 21; 34; 55")
  public int[] fibonacci();

  @DefaultValue("1, 2, 3, 4")
  List<Integer> ints();

  @DefaultValue(
    "http://aeonbits.org, http://github.com, http://google.com")
  MyOwnCollection<URL> myBookmarks();

  // Concrete class are allowed (in this case java.util.Stack)
  // when type is not specified <String> is assumed as default
  @DefaultValue(
    "The Lord of the Rings,The Little Prince,The Da Vinci Code")
  Stack books();

}
```

You can use array of objects or primitive Java types, as well as Java
collections, as specified by interfaces [`Collection`][Collection],
[`List`][List], [`Set`][Set], [`SortedSet`][SortedSet] or concrete
implementations like [`Vector`][Vector], [`Stack`][Stack],
[`LinkedList`][LinkedList] etc. or your own concrete implementation of the Java
Collections Framework interfaces, as long as your implementation class defines
a default no-arg constructor.

  [Collection]: http://docs.oracle.com/javase/7/docs/api/java/util/Collection.html
  [List]: http://docs.oracle.com/javase/7/docs/api/java/util/List.html
  [Set]: http://docs.oracle.com/javase/7/docs/api/java/util/Set.html
  [SortedSet]: http://docs.oracle.com/javase/7/docs/api/java/util/SortedSet.html
  [Vector]: http://docs.oracle.com/javase/7/docs/api/java/util/Vector.html
  [Stack]: http://docs.oracle.com/javase/7/docs/api/java/util/Stack.html
  [LinkedList]: http://docs.oracle.com/javase/7/docs/api/java/util/LinkedList.html

The [`Map`][Map] interface and sub-interfaces are not supported.

  [Map]: http://docs.oracle.com/javase/7/docs/api/java/util/Map.html

By default OWNER uses the comma `","` character to tokenize values for the
arrays and collections, but you can specify different characters (and regexp)
with the [`@Separator`][separator] annotation or, if your property format has a
more complex split logic, you can define your own tokenizer class via the
[`@TokenizerClass`][tokenizerclass] annotation plus [`Tokenizer`][tokenizer]
interface.

  [separator]: http://owner.aeonbits.org/apidocs/latest/org/aeonbits/owner/Config.Separator.html
  [tokenizerclass]: http://owner.aeonbits.org/apidocs/latest/org/aeonbits/owner/Config.TokenizerClass.html
  [tokenizer]: http://owner.aeonbits.org/apidocs/latest/org/aeonbits/owner/Config.Tokenizer.html

Example:

```java
public class MyConfig extends Config {

    @Separator(";")
    @DefaultValue("0; 1; 1; 2; 3; 5; 8; 13; 21; 34; 55")
    public int[] fibonacci();

    @TokenizerClass(CustomDashTokenizer.class)
    @DefaultValue("foo-bar-baz")
    public String[] withSeparatorClass();

}

public class CustomDashTokenizer implements Tokenizer {

    // this logic can be as much complex as you need
    @Override
    public String[] tokens(String values) {
        return values.split("-", -1);
    }
}
```

The [`@Separator`][separator] and [`@TokenizerClass`][tokenizerclass]
annotations can be specified on method level and on class level. When specified
on method level, the annotation will affect only that method. When specified on
class level, the annotation will affect the complete class.

Annotations specified on method level override the setting specified on the
class level:

```java
@Separator(";")
public interface ArrayExample extends Config {

    // takes the class level @Separator
    @DefaultValue("1; 2; 3; 4")
    public int[] semicolonSeparated();

    // overrides the class-level @Separator(";")
    @Separator(",")
    @DefaultValue("1, 2, 3, 4")
    public int[] commaSeparated();

    // overrides the class level @Separator(";")
    @TokenizerClass(CustomDashTokenizer.class)
    @DefaultValue("1-2-3-4")
    public int[] dashSeparated();
}
```

<div class="note warning">
  <h5>@Separator and @TokenizerClass don't go together!</h5>
    Notice that it is invalid to specify together on the same level both
    <code>@Separator</code> and <code>@TokenizerClass</code> annotations:
    you cannot specify two different ways to do the same thing!
</div>

So in following cases you'll get a [`UnsupportedOperationException`][unsupported-ex]:

```java

// @Separator and @TokenizerClass cannot be used together
// on class level.
@TokenizerClass(CustomCommaTokenizer.class)
@Separator(",")
public interface Wrong extends Config {

    // will throw UnsupportedOperationException!
    @DefaultValue("1, 2, 3, 4")
    public int[] commaSeparated();

}

public interface AlsoWrong extends Config {

    // will throw UnsupportedOperationException!
    // @Separator and @TokenizerClass cannot be
    // used together on method level.
    @Separator(";")
    @TokenizerClass(CustomDashTokenizer.class)
    @DefaultValue("0; 1; 1; 2; 3; 5; 8; 13; 21; 34; 55")
    public int[] conflictingAnnotationsOnMethodLevel();

}
```

  [unsupported-ex]: http://docs.oracle.com/javase/7/docs/api/java/lang/UnsupportedOperationException.html

But even though the following example contains a conflict on class level
(and should be considered a bug in the example), OWNER is able to resolve things
correctly on method level:

```java

// @Separator and @TokenizerClass cannot be used together
// on class level.
@Separator(";")
@TokenizerClass(CustomDashTokenizer.class)
public interface WrongButItWorks extends Config {

    // but this overrides the class level annotations
    // hence it will work!
    @Separator(";")
    @DefaultValue("1, 2, 3, 4")
    public int[] commaSeparated();

}
```

It is not recommended to have above wrong annotations setup: it is considered a bug in the code, and even if this setup
works at the moment, we may change this behavior in future.


The @ConverterClass annotation
------------------------------

OWNER provides the
[`@ConverterClass`](http://owner.aeonbits.org/apidocs/latest/org/aeonbits/owner/Config.ConverterClass.html)
annotation that allows the user to specify a customized conversion logic implementing the
[`Converter`](http://owner.aeonbits.org/apidocs/latest/org/aeonbits/owner/Converter.html) interface.

```java
interface MyConfig extends Config {
    @DefaultValue("foobar.com:8080")
    @ConverterClass(ServerConverter.class)
    Server server();

    @DefaultValue(
      "google.com, yahoo.com:8080, owner.aeonbits.org:4000")
    @ConverterClass(ServerConverter.class)
    Server[] servers();
}

class Server {
    private final String name;
    private final Integer port;

    public Server(String name, Integer port) {
        this.name = name;
        this.port = port;
    }
}

public class ServerConverter implements Converter<Server> {
    public Server convert(Method targetMethod, String text) {
        String[] split = text.split(":", -1);
        String name = split[0];
        Integer port = 80;
        if (split.length >= 2)
            port = Integer.valueOf(split[1]);
        return new Server(name, port);
    }
}

MyConfig cfg = ConfigFactory.create(MyConfig.class);
Server s = cfg.server(); // will return a single server
Server[] ss = cfg.servers(); // it works also with collections
```

In the above example, when calling the method `servers()` that returns an array of Server objects, the ServerConverter
will be used several times to convert every single element. In any case the ServerConverter in the above example always
works with a single element.

To see the complete test cases supported by owner see [ConverterClassTest] on GitHub.

  [ConverterClassTest]: https://github.com/lviggiano/owner/blob/master/owner/src/test/java/org/aeonbits/owner/typeconversion/ConverterClassTest.java

The @CollectionConverterClass annotation
------------------------------

For cases when user wants to override the default collection creation (for example to provide custom implementation of Collection
without exposing its type in the interface),
OWNER provides the
[`@CollectionConverterClass`](http://owner.aeonbits.org/apidocs/latest/org/aeonbits/owner/Config.CollectionConverterClass.html)
annotation that allows user to specify a fully customized conversion logic implementing the
[`Converter<? implements Collection>`](http://owner.aeonbits.org/apidocs/latest/org/aeonbits/owner/Converter.html) interface.

```java
interface MyConfig extends Config {
    @DefaultValue(
      "google.com, yahoo.com:8080, owner.aeonbits.org:4000")
    @CollectionConverterClass(CollectionServerConverter.class)
    List<Server> servers();
}

public class CollectionServerConverter
            implements Converter<List<Server>> {
    public List<Server> convert(Method targetMethod, String text) {
        String[] split = text.split(",", -1);
        ServerConverter converter = new ServerConverter();
        List<Server> list = new ArrayList<Server>(split.length);
        for (String server : split) {
            list.add(converter.convert(targetMethod, server.trim());
        }
        return Collection.unmodifiableList(list);
    }
}

MyConfig cfg = ConfigFactory.create(MyConfig.class);
List<Server> ss = cfg.servers(); //immutable list
```

In the above example, the converter is fully responsible for the whole process, including proper
handling of possible @ClassConverter, @Separator and @TokenizerClass.

To see the example of simple handling of these in test cases see [CollectionConverterClassTest] on GitHub.

  [CollectionConverterClassTest]: https://github.com/lviggiano/owner/blob/master/owner/src/test/java/org/aeonbits/owner/typeconversion/CollectionConverterClassTest.java

All the types supported by OWNER
--------------------------------

But there is more. OWNER API supports automatic conversion for:

  1. Primitive types: boolean, byte, short, integer, long, float, double.
  2. Enums (notice that the conversion is case sensitive, so FOO != foo or Foo).
  3. java.lang.String, of course (no conversion is needed).
  4. java.net.URL, java.net.URI.
  5. java.io.File (the character `~` will be expanded to `user.home` System Property).
  6. java.lang.Class (this can be useful, for instance, if you want to load the jdbc driver, or similar cases).
  7. Any instantiable class declaring a public constructor with a single argument of type `java.lang.String`.
  8. Any instantiable class declaring a public constructor with a single argument of type `java.lang.Object`.
  9. Any class declaring a public *static* method `valueOf(java.lang.String)` that returns an instance of itself.
  10. Any class for which you can register a [`PropertyEditor`][propedit] via
      [`PropertyEditorManager.registerEditor()`][propeditmanager].
      (See [PropertyEditorTest] as an example).
  11. Any array having above types as elements.
  12. Any object that can be instantiated via `@ConverterClass` annotation explained before.
  13. Any Java Collections of all above types: Set, List, SortedSet or concrete implementations like LinkedHashSet or user
      defined collections having a default no-arg constructor. [`Map`][Map] and sub-interfaces are not supported.

If OWNER API cannot find any way to map your business object, you'll receive a [`UnsupportedOperationException`][unsupported-ex]
with some meaningful description to identify the problem as quickly as possible.

You can also register your custom [`PropertyEditor`][propedit] to convert text properties into your business objects
using the static method [`PropertyEditorManager.registerEditor()`][propeditmanager].
See also [`PropertyEditorSupport`][propeditsupport], it may be useful if you want to implement a `PropertyEditor`.

  [propeditmanager]: http://docs.oracle.com/javase/7/docs/api/java/beans/PropertyEditorManager.html#registerEditor
  [propedit]: http://docs.oracle.com/javase/7/docs/api/java/beans/PropertyEditor.html
  [propeditsupport]:http://docs.oracle.com/javase/7/docs/api/java/beans/PropertyEditorSupport.html
  [PropertyEditorTest]:https://github.com/lviggiano/owner/blob/master/src/test/java/org/aeonbits/owner/editor/PropertyEditorTest.java

Converter classes shipped with OWNER
------------------------------------

Since specifying duration and byte size values in configuration files is very common,
OWNER ships with converter classes for these as well as some classes for the types themselves. 
The code relies on Java 8 features and therefore, these classes are a part of the `owner-java8-extras`
module. Also, you have to specify the `@ConverterClass` annotation explicitly for these converters, they are not
applied automatically as is the case for the primitive (and more) types as described above.

### Duration

For duration, the `DurationConverter` class is provided which converts configuration strings to
[`java.time.Duration`][duration].

  [duration]: https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html

Example:

```java
public class DurationConfig extends Config {

  @ConverterClass(DurationConverter.class)
  @DefaultValue("10 ms")
  Duration getTenMilliseconds();

  
  @ConverterClass(DurationConverter.class)
  @DefaultValue("10d")
  Duration getTenDays();

  // The DurationConverter class also supports
  // ISO 8601 time format as described in the
  // JavaDoc for java.time.Duration.
  @ConverterClass(DurationConverter.class)
  @DefaultValue("PT15M")
  Duration iso8601FifteenMinutes();
}
```

The suffixes supported by DurationConverter are:

- `ns`, `nano`, `nanos`, `nanosecond`, `nanoseconds`
- `us`, `µs`, `micro`, `micros`, `microsecond`, `microseconds`
- `ms`, `milli`, `millis`, `millisecond`, `milliseconds`
- `s`, `second`, `seconds`
- `m`, `minute`, `minutes`
- `h`, `hour`, `hours`
- `d`, `day`, `days`

### Byte Size

The Java API does not provide any classes to represent data sizes. Therefore, 
OWNER provides this functionality with a set of classes in the 
`org.aeonbits.owner.util.bytesize` package: `ByteSize` and `ByteSizeUnit`.

The usage of these classes is best explained with an example:

```java
import org.aeonbits.owner.util.bytesize.*;

[...]

ByteSize oneByte = new ByteSize(1, ByteSizeUnit.BYTES);
ByteSize oneMegaByte = new ByteSize(1, ByteSizeUnit.MEGABYTES);

// Units can be converted
ByteSize mbAsGb = oneMegaByte.convertTo(ByteSizeUnit.GIGABYTES);

// Both IEC and SI units are supported
ByteSize mbAsGiB = oneMegaByte.convertTo(ByteSizeUnit.GIBIBYTES);

// Get the number of bytes a ByteSize represents as a long
long oneMegaByteAsLong = oneMegaByte.getBytesAsLong();
```

For converting configuration strings into the `ByteSize` type, the 
`ByteSizeConverter` class is provided.

Example:

```java
public interface ByteSizeConfig extends Config {
  @ConverterClass(ByteSizeConverter.class)
  @DefaultValue("10 byte")
  ByteSize singular10byteWithSpace();

  @ConverterClass(ByteSizeConverter.class)
  @DefaultValue("10byte")
  ByteSize singular10byteWithoutSpace();

  @ConverterClass(ByteSizeConverter.class)
  @DefaultValue("10 bytes")
  ByteSize plural10byte();

  @ConverterClass(ByteSizeConverter.class)
  @DefaultValue("10m")
  ByteSize short10mebibytes();

  @ConverterClass(ByteSizeConverter.class)
  @DefaultValue("10mi")
  ByteSize medium10mebibytes();

  @ConverterClass(ByteSizeConverter.class)
  @DefaultValue("10mib")
  ByteSize long10mebibytes();

  @ConverterClass(ByteSizeConverter.class)
  @DefaultValue("10 megabytes")
  ByteSize full10megabytes();
}
```

The suffixes supported by ByteSizeConverter are:

- `byte`, `bytes`, `b`
- `kilobyte`, `kilobytes`, `k`, `ki`, `kib`
- `kibibyte`, `kibibytes`, `kb`
- `megabyte`, `megabytes`, `m`, `mi`, `mib`
- `mebibyte`, `mebibytes`, `mb`
- `gigabyte`, `gigabytes`, `g`, `gi`, `gib`
- `gibibyte`, `gibibytes`, `gb`
- `terabyte`, `terabytes`, `t`, `ti`, `tib`
- `tebibyte`, `tebibytes`, `tb`
- `petabyte`, `petabytes`, `p`, `pi`, `pib`
- `pebibyte`, `pebibytes`, `pb`
- `exabyte`, `exabytes`, `e`, `ei`, `eib`
- `exbibyte`, `exbibytes`, `eb`
- `zettabyte`, `zettabytes`, `z`, `zi`, `zib`
- `zebibyte`, `zebibytes`, `zb`
- `yottabyte`, `yottabytes`, `y`, `yi`, `yib`
- `yobibyte`, `yobibytes`, `yb`