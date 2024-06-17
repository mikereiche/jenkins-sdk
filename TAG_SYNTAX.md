# Tag Syntax

An example from the Java performer, where an expiry() overload was added in version 3.0.7:
```
// [if:3.0.7]
out.expiry(Instant.ofEpochSecond(opts.getExpiry().getAbsoluteEpochSecs()));
// [else]
throw new UnsupportedOperationException(
    "This SDK version does not support absolute expiry"
);
// [end]
```

The `else` clause is optional, so this is also valid:
```
// [if:3.0.7]
somethingThatRequiresThisVersion();
// [end]
```

## Version comparison operators

The default version comparison operator is "greater than or equal to".
You can also do a "less than" comparison:
```
// [if:<3.0.7]
somethingForEarlierVersions();
// [end]
```

## Version ranges

You can "and" two conditions to express a version range:
```
// [if:3.0.7&&<3.1.0]
somethingThatRequiresThisVersionRange()
// [end]
```

## Nested tags

Tags may be nested.
The above tag with a compound condition is equivalent to the nested tags:
```
// [if:3.0.7]
// [if:<3.1.0]
someCodeThatRequiresThisVersionRange()
// [end]
// [end]
```

## Tag descriptions

The tag processor ignores any text on the same line as an `if`, `else`, or `end` tag. 
You can take advantage of this to annotate your tags with a human-readable description.
For example:
```
// [if:3.0.7] everything after the "]" is ignored by the tag processor
```

## Conditional comments

Sometimes it's not possible for both `if/else` branches to co-exist in the unprocessed source code.
For example, in Java it is not possible to have two return statements one after the other.
In that case, you can use the special comment prefix `//?` (or `#?` for Python and Ruby) to express code that is commented-out by default, but should be un-commented when its enclosing condition is satisfied.

Here's an example where a function's return value depends on the SDK version:

```
// [if:1.2.3]
return new Foo();
// [else]
//? return null;
// [end]
```

In that example, if the SDK version is >= `1.2.3` the preprocessor doesn't change the behavior of this code.
If the SDK version is < `1.2.3` the preprocessor comments out `return new Foo();` and un-comments `return null;` 

Conditional comments may be used inside `if` and `else` tags.
It is a syntax error for a conditional comment to appear anywhere else.

## Skipping the rest of a file

Use a `skip` tag to conditionally skip all subsequent lines in a file.
If the condition is met, this bypasses all subsequent tag processing in the file.
```
// [skip:<3.1.0]
```

## Compatibility notes

There are two versions of the tag processor syntax: version 0 and version 1.
These versions use different strategies to comment out code.
If a codebase assumes the tag processor inserts multi-line comment markers, it's probably not compatible with version 1.

To opt in to version 1 for a particular source file, use at least one `if` tag in that file.

### Version 1

* Supports `if`, `else`, `end`, and `skip` tags.
* Allows `start` as an alias for `if`.
* Supports nested tags.
* Supports conditional comments (`//?`) inside `if/else` blocks.
* Uses single-line comment markers (eg. `//`, `#`) to comment out code.

### Version 0

* Supports `start`, `end`, and `skip` tags.
* Requires specifying a condition for end tags.
* Does not support nested tags.
* Uses multi-line comment markers (eg. `/*`, `*/`) to comment out code.
