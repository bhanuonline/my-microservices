OCC = Omni Commerce Connect

🔹 Important OCC Extensions
| Extension              | Purpose                     |
| ---------------------- | --------------------------- |
| `commercewebservices`  | Main OCC extension          |
| `ycommercewebservices` | Default REST implementation |
| `webservicescommons`   | Base web services layer     |


OAuth2AuthenticationProcessingFilter
BaseSiteFilter
SessionFilter

DataMapper
ProductData → ProductWsDTO

3️⃣ Orika Mapping Strategy
Orika:
Scans fields via reflection
Matches by field name
Generates mapping bytecode at runtime
Caches mapping

🔹 Where is Mapping Defined?
Mapping is configured automatically for:
Same field names
Same data types
If field names differ:
You define custom mapping in:
*-web-spring.xml

use for json converter Converts DTO → JSON.
MappingJackson2HttpMessageConverter

🔥 One-Line Understanding

DataMapper (Orika) → copies data from Data → DTO
FieldSet → decides what to include in response


**OAuth DB Tables**

| Table              | Purpose       |
| ------------------ | ------------- |
| OAuthClientDetails | Client config |
| OAuthAccessToken   | Token storage |
| OAuthRefreshToken  | Refresh token |


🔹 OAuth Grant Types

| Type               | Use Case         |
| ------------------ | ---------------- |
| password           | Mobile login     |
| client_credentials | System to system |
| refresh_token      | Token renewal    |

🔥 Very Important Interview Point
OCC is:
Stateless
Token-based
No JSESSIONID dependency


#Test
INSERT_UPDATE ProductReview;code[unique=true];product(code);user(uid);rating;headline;reviewText;reviewDate[dateformat=dd.MM.yyyy];approved
;rev_001;0400578909999;bhanu.mmmec2013@gmail.com;5;Great Product!;Really liked the quality.;15.03.2026;true
;rev_002;155068597;bhanu.mmmec2013@gmail.com;4;Good one;Value for money.;16.03.2026;true


DEBUG [hybrisHTTP2] [DefaultMapperFactory] No mapper registered for (ProductReviewData, ProductReviewWsDTO): attempting to generate
DEBUG [hybrisHTTP2] [ClassMapBuilder] ClassMap created:
ClassMapBuilder.map(ProductReviewData, ProductReviewWsDTO)
.field( comment(String), comment(String) )
.field( headline(String), headline(String) )
.field( id(String), id(String) )
.field( rating(Integer), rating(Integer) )
DEBUG [hybrisHTTP2] [MapperGenerator] Generating new mapper for (ProductReviewData, ProductReviewWsDTO)
Orika_ProductReviewWsDTO_ProductReviewData_Mapper84646357840209$24.mapAToB(ProductReviewData, ProductReviewWsDTO) {
Field(comment(String), comment(String)) : copying String by reference
Field(headline(String), headline(String)) : copying String by reference
Field(id(String), id(String)) : copying String by reference
Field(rating(Integer), rating(Integer)) : copying Integer by reference
}
Orika_ProductReviewWsDTO_ProductReviewData_Mapper84646357840209$24.mapBToA(ProductReviewWsDTO, ProductReviewData) {
Field(comment(String), comment(String)) : copying String by reference
Field(headline(String), headline(String)) : copying String by reference
Field(id(String), id(String)) : copying String by reference
Field(rating(Integer), rating(Integer)) : copying Integer by reference
}
Types used: [String, Integer]
Filters used: [com.landmarkshops.commercews.mapping.filters.GeneralFieldFilter@7c4ad311]
DEBUG [hybrisHTTP2] [MapperFacadeImpl] MappingStrategy resolved and cached:
Inputs:[ sourceClass: de.hybris.platform.commercefacades.product.data.ProductReviewData, sourceType: Object, destinationType: ProductReviewWsDTO]
Resolved:[ strategy: InstantiateAndUseCustomMapperStrategy, sourceType: ProductReviewData, destinationType: ProductReviewWsDTO, mapper: GeneratedMapper<ProductReviewData, ProductReviewWsDTO> {usedConverters: [], usedMappers: [], usedMapperFacades: [], usedTypes: [String, Integer] }, mapReverse?: false]






