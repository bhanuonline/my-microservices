What is hybris component ?
a Component is a CMS (Content Management System) building block used to display content on a website page
1️⃣ What is a Hybris Component?

A Hybris CMS Component:

Represents one piece of content on a page
Is configurable from Backoffice / SmartEdit
Can be reused across multiple pages
Is rendered via JSP / Velocity / Frontend template

Examples:
Banner
Product Carousel
Navigation Menu
CMS Paragraph
Login Component

2️⃣ Component in Page Hierarchy
Page
└── Content Slot
    └── Component
        └── Component Attributes (text, image, link, product, etc.)
👉 Page = What page
👉 Slot = Where on the page
👉 Component = What content

3️⃣ Types of Hybris Components
🔹 CMSComponent (Base Type)
🔹 Simple Components
🔹 Product-related Components
🔹 Navigation Components
🔹 Custom Components

6️⃣ Difference: Component vs Content Slot

| Component         | Content Slot        |
| ----------------- | ------------------- |
| Actual content    | Container           |
| Has business data | Has layout position |
| Reusable          | Page-specific       |

Ex:
Homepage
├── HeaderSlot
│    └── NavigationComponent
├── BannerSlot
│    └── SimpleBannerComponent
├── ProductSlot
│    └── ProductCarouselComponent
└── FooterSlot
└── CMSParagraphComponent

9️⃣ Common Interview Questions

Q: Can multiple components be in one slot?
✔ Yes

Q: Can one component be used in multiple pages?
✔ Yes

Q: Is controller mandatory?
❌ No (only if logic needed)

Q: Difference between CMSComponent and SimpleCMSComponent?
SimpleCMSComponent is a basic extension for static content

2️⃣ Page
✅ What is a Page?

A Page represents a full webpage (or logical page) like:
Homepage
Product Detail Page (PDP)
Category Page (PLP)
Login Page
Content Page

| Page Type    | Use Case                   |
| ------------ | -------------------------- |
| ContentPage  | Static pages (Home, About) |
| ProductPage  | Product detail pages       |
| CategoryPage | Category listing pages     |
| CatalogPage  | System-driven pages        |

🔹 Page Attributes
Restrictions (user, time, device)

3️⃣ Content Slot
✅ What is a Content Slot?
A Content Slot is a container/placeholder on a page that defines layout position.
Examples:
HeaderSlot
FooterSlot
LeftNavigationSlot
MainBannerSlot


4️⃣ Component
✅ What is a Component?
A Component is the actual content unit displayed on the page.
Examples:
Banner
Product Carousel
Navigation Menu
Text Paragraph


🔹 Component Characteristics
Reusable
Configurable in Backoffice
Can appear in multiple slots/pages

6️⃣ Responsibilities Comparison

| Aspect            | Page           | Content Slot  | Component      |
| ----------------- | -------------- | ------------- | -------------- |
| Represents        | Whole page     | Section/area  | Actual content |
| Business editable | Yes            | Usually fixed | Yes            |
| Reusable          | Limited        | Yes           | Yes            |
| Holds data        | Metadata       | No            | Yes            |
| UI logic          | No             | No            | Yes            |
| Created by        | Business / Dev | Dev           | Business / Dev |

8️⃣ Common Interview Traps 🚨
❓ Can a component exist without a page?
✔ Yes (created but not assigned)
❓ Can a slot exist without a page?
❌ No (slot belongs to template/page)
❓ Can a component be in multiple slots?
✔ Yes
❓ Can multiple components be in one slot?
✔ Yes


9️⃣ Page Template vs Page (Important!)
Page Template defines slots
Page uses template and fills slots with components

Page Template
└── Slots defined

Page
└── Components assigned to slots

Page defines the page, Slot defines the position, Component defines the content

End-to-end CMS rendering flow:
1️⃣ User Hits a URL
What happens
Request comes to Spring MVC DispatcherServlet
Goes through filters (security, language, currency, site)
2️⃣ Site, Language, Catalog Resolution

Hybris determines:
BaseSite (electronics, apparel, etc.)
Language (en, hi)
Currency (INR)
Catalog Versions (Online)
📌 Done by:
BaseSiteService
CatalogVersionService

3️⃣ CMS Page Resolution
Hybris tries to find which CMS page to render

Logic:
Match page label = /home

Check page type:
ContentPage
ProductPage
CategoryPage

Check restrictions
Time
User group
Device
Ensure page is Approved + Online
📌 Key class:DefaultCMSPageService

4️⃣ Page Template Resolution
Once page is found:
Hybris gets Page Template
Template defines content slots
HomepageTemplate
├── HeaderSlot
├── BannerSlot
├── ProductSlot
└── FooterSlot

5️⃣ Content Slot Resolution

For each slot:

Page-specific slot overrides checked

Fallback to template slots
Slots contain list of components
service :CMSContentSlotService


6️⃣ Component Resolution

For each slot:
Fetch assigned components
Apply component-level restrictions
Sort by position
service :CMSComponentService

7️⃣ Controller Resolution (Important 🔥)
Two possible paths:
🔹 A) Component has Controller
/view/MyCustomComponent

AbstractCMSComponentController
fillModel() executed
Data added to model

🔹 B) No Controller
Direct JSP rendering
Model contains component object

📌 Mapping:
Component → Controller → JSP

Ex:
<cms:pageSlot position="BannerSlot">
<cms:component />
</cms:pageSlot>

9️⃣ Data Binding to UI
${component.title}
${component.media.url}

Complete Flow (One View)
URL Hit
↓
Filters (site, language, catalog)
↓
CMS Page Resolution
↓
Page Template
↓
Content Slots
↓
Components
↓
Controller (optional)
↓
JSP Rendering
↓
HTML Response

❓ Where are restrictions applied?
✔ Page & Component level

❓ Can component render without controller?
✔ Yes

❓ Slot vs Component logic?
✔ Slot = layout
✔ Component = data + UI

