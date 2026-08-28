# .    What is the Type System?
A Type defines the structure/metadata of an object.
Hybris doesn't use traditional JPA/Hibernate for data modeling. It has its own type system defined in items.xml files. When you define a type, Hybris:
1. Creates the database table for you
2. Generates Java model classes (jalo & model)
3. Handles persistence automatically
4. At its core, the Type System is Hybris's data modeling layer — it defines what data exists and how it's structured.

The 7 Types in Hybris
A Type is a blueprint/schema definition (like a class in Java). Hybris has several kinds:
┌─────────────────────────────────────────────────┐
│                 HYBRIS TYPE SYSTEM               │
├──────────────┬──────────────────────────────────┤
│ ItemType     │ Main type — like a DB table/Java class │
│ RelationType │ Defines relationships between ItemTypes │
│ EnumType     │ Fixed set of values (like Java enum)    │
│ CollectionType│ List/Set of another type               │
│ MapType      │ Key-value pair type                     │
│ AtomicType   │ Primitive types (String, Integer, etc.) │
│ ComposedType │ Legacy name for ItemType (Jalo layer)   │
└──────────────┴──────────────────────────────────┘


For example:

This is the core building block — equivalent to a database table + Java class.

<itemtype code="Product" extends="GenericItem"
autocreate="true" generate="true"
jaloclass="de.hybris.platform.jalo.product.Product">

      <deployment table="Products" typecode="1"/>

      <attributes>
          <attribute qualifier="code" type="java.lang.String">
              <persistence type="property"/>
              <modifiers read="true" write="true" search="true" optional="false" unique="true"/>
          </attribute>
          <attribute qualifier="name" type="localized:java.lang.String">
              <persistence type="property"/>
          </attribute>
      </attributes>
  </itemtype>

Here:
Product
    ├── code
    └── name

Product is the type.
Think of it like a Java class:

class Product {
String code;
String name;
}

Key elements:

┌───────────────────┬───────────────────────────────────────────────────┐
│      Element      │                      Purpose                      │
├───────────────────┼───────────────────────────────────────────────────┤
│ code              │ Unique name of the type                           │
├───────────────────┼───────────────────────────────────────────────────┤
│ extends           │ Parent type (inheritance)                         │
├───────────────────┼───────────────────────────────────────────────────┤
│ autocreate="true" │ Create this type during system initialization     │
├───────────────────┼───────────────────────────────────────────────────┤
│ generate="true"   │ Generate Java model classes                       │
├───────────────────┼───────────────────────────────────────────────────┤
│ deployment table  │ Actual DB table name                              │
├───────────────────┼───────────────────────────────────────────────────┤
│ typecode          │ Unique integer ID (must be unique across system!) │
└───────────────────┴───────────────────────────────────────────────────┘

Attribute properties:

┌─────────────────────────────┬───────────────────────────────────────────────────────┐
│          Property           │                        Meaning                        │
├─────────────────────────────┼───────────────────────────────────────────────────────┤
│ qualifier                   │ Field name                                            │
├─────────────────────────────┼───────────────────────────────────────────────────────┤
│ type                        │ Data type (java.lang.String, java.lang.Integer, etc.) │
├─────────────────────────────┼───────────────────────────────────────────────────────┤
│ localized:                  │ Prefix means value differs per language               │
├─────────────────────────────┼───────────────────────────────────────────────────────┤
│ persistence type="property" │ Stored in DB column                                   │
├─────────────────────────────┼───────────────────────────────────────────────────────┤
│ persistence type="dynamic"  │ Computed at runtime (not in DB)                       │
├─────────────────────────────┼───────────────────────────────────────────────────────┤
│ optional="false"            │ Required field                                        │
├─────────────────────────────┼───────────────────────────────────────────────────────┤
│ unique="true"               │ Must be unique                                        │
└────────────────────────────

Item

An Item is a single instance of an ItemType (like an object of a class). For example:
- The ItemType Product defines that products have a code, name, price
- A specific product "Blue T-Shirt" with code TSH001 is an Item

Where They're Defined

Types are declared in items.xml files inside each extension:

<itemtype code="MyCustomProduct"
extends="Product"
autocreate="true"
generate="true">
<attributes>
<attribute qualifier="customField" type="java.lang.String">
<persistence type="property"/>
</attribute>
</attributes>
</itemtype>

Key points:
- extends — inheritance, just like Java classes
- autocreate="true" — creates the type in DB on system update
- generate="true" — generates Java model classes (MyCustomProductModel.java)
- persistence type="property" — stored as a DB column
- persistence type="dynamic" — computed at runtime via a handler

The Lifecycle

items.xml  →  ant build  →  Generated Model classes (.java)
→  System Update  →  DB tables/columns created


1. What problem does AtomicType solve?
Hybris needs to know the basic data type of an attribute.

For example:
Product
├── code        → String
├── price       → Double
├── quantity    → Integer
├── active      → Boolean
└── createdDate → Date

Hybris needs metadata saying:
"code contains a String, price contains a Double, etc."
These basic/simple types are represented in the Hybris type system as AtomicTypes.

2. What is the benefit?
It gives Hybris a common type-system representation for simple Java values.
   This allows Hybris to know:

What kind of value the attribute accepts
How the value should be persisted
How the value should be converted
What Java type the generated model should expose

3. Where do we use it in Hybris?
You use AtomicTypes whenever an attribute contains a simple value.

# One subtle point: AtomicType doesn't mean "primitive Java type." String, Integer, Double, Boolean, etc. are Java object types, but Hybris treats them as atomic values in its type system.

If String, Integer, Double, etc. already exist in Java, why does Hybris need the concept of AtomicType?

Because Hybris has its own Type System. It needs to understand types independently of Java classes.
Think of it as registering a Java class so Hybris knows it exists. That's it.

Hybris doesn't know what java.lang.String or java.lang.Integer is until you tell it. So core-items.xml registers them:

<atomictype class="java.lang.String" />   <!-- now Hybris knows "String" -->
<atomictype class="java.lang.Integer" />  <!-- now Hybris knows "Integer" -->

After registration, you can use String as an attribute type:
<attribute qualifier="name" type="java.lang.String" />

1. Java knows this
   String name;
   Integer quantity;
   Double price;

Java knows:

name     → String
quantity → Integer
price    → Double

But Hybris also needs to know this information at the Hybris type-system level.

So Hybris represents:

Java String
↓
Hybris AtomicType: java.lang.String
2. What benefit does Hybris get?

Suppose you define this in items.xml:

<attribute qualifier="name"
type="java.lang.String"/>

Hybris can now store metadata like:

Product
|
└── name
|
├── Type → String
├── readable → true
├── writable → true
├── optional → ...
└── persistence → ...

That information belongs to the Hybris Type System, not just Java.

So Hybris can use it for:

generating ProductModel
validating attribute values
persistence
FlexibleSearch
ImpEx
Backoffice
type-system metadata
runtime type checking

4. Very important: AtomicType is metadata

AtomicType doesn't replace String.

It is Hybris's metadata representation of a simple value type.

So when you see:

type="java.lang.String"

don't think:

"Hybris created another String."

Instead think:

"Hybris registered/understands java.lang.String as an atomic value type in its own type system."
why need additional value in hybris ?

Exactly. The reason is that Java only needs to know how to handle the value, while Hybris needs to know how that value behaves inside the commerce platform.
1. What problem?

Suppose Java sees:

String code = "P1001";

Java only needs to know:

code → String

But Hybris needs to answer more questions:

code
↓
Is it persisted?
Is it searchable?
Can it be null?
Is it unique?
Can it be modified?
How should it be stored?
What type does Hybris consider it?

Java's String doesn't contain this Hybris-specific information.

2. Why does Hybris need this information?

Because Hybris is not just a Java application. It has its own:

Type System
Persistence layer
FlexibleSearch
ImpEx
Backoffice
Model layer
Dynamic type handling

AtomicType
↓
"What is the value?"
↓
String / Integer / Boolean / Date


AttributeDescriptor
↓
"How does this attribute behave?"
↓
optional / unique / read / write / persistence

"AtomicType is Hybris's representation of simple Java value types in its Type System, with additional metadata needed by Hybris."