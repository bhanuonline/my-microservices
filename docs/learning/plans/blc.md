LMG Microservices — System Architecture

Counts: 51 backend microservices + 2 API gateways + 1 auth service + 2 frontends + Solr + databases.

┌───────────────────────────────────────────────────────────────────────────────────┐
│                                 CLIENTS / FRONTENDS                               │
│                                                                                   │
│   ┌─────────────────────┐                       ┌──────────────────────────┐      │
│   │  AdminWeb (React)   │                       │ commerce-nextjs-starter  │      │
│   │  back-office UI     │                       │  customer storefront     │      │
│   └──────────┬──────────┘                       └────────────┬─────────────┘      │
└──────────────┼─────────────────────────────────────────────-─┼────────────────────┘
│                                               │
▼                                               ▼
┌──────────────────────────────┐               ┌──────────────────────────────────┐
│  Admin Gateway (Spring Cloud │               │  Commerce Gateway (Spring Cloud  │
│  Gateway) — 40 routes        │               │  Gateway) — 42 routes            │
└──────────────┬───────────────┘               └────────────────┬─────────────────┘
│             ┌──────────────────────┐           │
└────────────▶│  AuthenticationSvc   │◀──────────┘
│  (OAuth2 / token)    │
└──────────────────────┘
│
┌───────────────────────────────┴──────────────────────────────────┐
▼                                                                  ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         MICROSERVICES (51 services)                             │
│                                                                                 │
│  COMMERCE CORE          CATALOG / PRICING      CUSTOMER / AUTH                  │
│  ├─ cart                ├─ catalog             ├─ customer                      │
│  ├─ cartops             ├─ catalogbrowse       ├─ customer-enquiry              │
│  ├─ order               ├─ pricing             ├─ adminuser                     │
│  ├─ orderops            ├─ offer               ├─ adminnav                      │
│  ├─ paymenttransaction  ├─ campaign            └─ tenant                        │
│  ├─ shipping            ├─ menu                                                 │
│  ├─ delivery            ├─ content             SEARCH / INDEXING                │
│  ├─ collectionpoint     ├─ asset               ├─ search ──────► Solr           │
│  └─ giftcard            ├─ personalization     ├─ indexer                       │
│                         └─ productdata         ├─ algolia-indexer ──► Algolia   │
│  INVENTORY                                     ├─ bloomreach-indexer ─► BR      │
│  ├─ inventory           LOYALTY / PAYMENT      └─ fashion-indexer               │
│  ├─ inventory-manager   ├─ shukranloyalty                                       │
│  ├─ fashion-inventory   ├─ shukranpay          OPS / PLATFORM                   │
│  └─ vendor              ├─ creditaccount       ├─ scheduledjob                  │
│                         └─ ratingandreview     ├─ notification                  │
│  INTEGRATION                                   ├─ metadata                      │
│  ├─ hybris-integration  ANALYTICS              ├─ sandbox                       │
│  ├─ sterling ──────────►IBM Sterling           ├─ import                        │
│  ├─ orion               ├─ big-query ──► GCP   ├─ datafeed                      │
│  ├─ dynamicaction       └─ nps                 ├─ thirdpartytoken               │
│  └─ qordobatranslation                         └─ ...                           │
└─────────────────────────────────────────────────────────────────────────────────┘
│
┌───────────────────────────────┼─────────────────────────────────┐
▼                               ▼                                 ▼
┌──────────────────┐         ┌─────────────────────┐           ┌────────────────────┐
│  PostgreSQL      │         │  Solr (custom image)│           │  External Systems  │
│  (per-service    │         │  search index       │           │  ├─ Algolia        │
│   liquibase      │         └─────────────────────┘           │  ├─ Bloomreach     │
│   changelogs)    │                                           │  ├─ IBM Sterling   │
└──────────────────┘                                           │  ├─ Hybris         │
│  ├─ GCP BigQuery   │
│  ├─ Qordoba        │
│  └─ Dynamic Action │
└────────────────────┘

DEPLOYMENT / TOOLING (cross-cutting)
• devops-helm-charts → Kubernetes deployment
• client-terraform-starter → cloud infra (IaC)
• New Relic agents bundled in gateway + auth Dockerfiles
• Liquibase changelogs per service for schema migration

Summary

- 2 entry-point UIs: AdminWeb (React) and commerce-nextjs-starter (Next.js).
- 2 gateways: MicroservicesGateways/admin (40 routes) and MicroservicesGateways/commerce (42 routes), both Spring Cloud Gateway.
- 1 auth service: AuthenticationServicesImage (OAuth2 token endpoint shared by both gateways).
- 51 backend microservices under Microservices/services/ (commerce core, catalog/pricing, customer, inventory, search/indexers, loyalty/payment, integration, ops/platform).
- Data stores: PostgreSQL per service (Liquibase changelogs visible in your git status) + a custom Solr im
- External integrations: Algolia, Bloomreach, IBM Sterling, SAP Hybris, Google BigQuery, Qordoba, DynamicAction.

OMS / WMS / RMS / Hybris / eFulfill — Integration Map

Based on the actual wiring in this repo (sterling, hybris-integration, order, inventory, inventory-manager, notification services):

                        ┌──────────────────────────────────────────────┐
                        │  Storefront (Next.js)  /  AdminWeb (React)   │
                        └────────────────────┬─────────────────────────┘
                                             │
                              ┌──────────────┴────────────────┐
                              ▼                               ▼
                   ┌─────────────────────┐         ┌─────────────────────┐
                   │  Commerce Gateway   │         │   Admin Gateway     │
                   └──────────┬──────────┘         └──────────┬──────────┘
                              │                               │
                              ▼                               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                         BLC MICROSERVICES (BLC = source-system)          │
│  cart  ─►  cartops  ─►  order  ─►  orderops  ─►  paymenttransaction      │
│           inventory ◄─►  inventory-manager  ◄─►  fashion-inventory       │
│           notification     shipping     delivery     collectionpoint     │
└──────┬───────────────────────────────────────────────────────────┬───────┘
│                                                           │
│ Kafka events                                              │ REST
▼                                                           ▼
┌──────────────────────────┐                        ┌─────────────────────────────┐
│   sterling microservice  │                        │ hybris-integration service  │
│  (adapter: BLC ↔ OMS)    │                        │  (adapter: BLC ↔ Hybris)    │
│                          │                        │                             │
│  Producers:              │                        │  Outbound REST:             │
│   • publish-cancellation-oms-event                │   • /landmarkshopscommercews│
│   • order-submitted-event│                        │     /eg/v2/maxeg/... orders │
│   • triggeredJobEvent    │                        │     refundoptions, addresses│
│                          │                        │                             │
│  Inbound endpoints:      │                        └──────────────┬──────────────┘
│   • LMGOMSEndpoint       │                                       │
│   • LMGOMSReturnEndPoint │                                       ▼
│   • LMGOMSShortPickSvc   │                        ┌─────────────────────────────┐
└────┬─────────────────┬───┘                        │   SAP Hybris (legacy ecom)  │
│                 │                            │   uat1.maxfashion.com       │
│ REST            │ Kafka                      │   landmarkshopscommercews   │
▼                 ▼                            └─────────────────────────────┘
┌────────────────────────────┐   ┌──────────────────────────────────────────────┐
│   IBM STERLING OMS         │   │            WMS (Warehouse Mgmt)              │
│   http://161.156.104.9:7080│   │  consumes "order-submitted-event" topic via  │
│   /eai/hybris/ordercreate  │   │  lmgOrderSubmittedToWarehouseProducer        │
│   /eai/hybris/ordercancel  │   │                                              │
│   /fulfillment-operations  │   │  Physical warehouses (per concept × country):│
│   /order-fulfillments      │   │   CP_WAREHOUSE_{OM,EG,KW,BH,QA}              │
│   /generate-invoice-number │   │   MAX_WAREHOUSE_{OM,EG,KW,…}                 │
│   /rt-logs/rt-log-retry    │   │   HOMECENTRE_WAREHOUSE_{OM,EG,…}             │
│                            │   │   HOMEBOXSTORES_WAREHOUSE_OM …               │
│   OMS pushes back:         │   │                                              │
│    • fulfillment status    │   │   dg-warehouse-mapping resolves DG codes →   │
│    • short-pick            │   │   warehouse LOCATION_NUMBERs                 │
│    • cancellation          │   └──────────────────────────────────────────────┘
│    • return / refund       │
└────────┬─────────────────┬─┘
│                 │
│ Kong API GW     │ (used for return/refund enquiry, NDR validate)
▼                 ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  Kong gateway @ devapi3.landmarkgroup.com  /  apidev.landmarkgroup.com       │
│   /landmarkgroup/retail-sit/v3/customer-orders                               │
│   /landmarkgroup/retail-sit/v3/customer-return-orders                        │
│   return-refund-enquiry, refund-status, NDR validate                         │
└──────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│  RMS (Oracle Retail Merchandising)  — source of master retail data     │
│   • CONCEPT_RMS_CODE → department code (used in OMS order populator)   │
│   • rmsStoreCode      → dark-store ShipNode for QCommerce fulfillment  │
│   • web-store-id (88880 OM, 88881 QA, 88882 EG, 88883 KW, 88884 BH)    │
│   • enterprise-code (LMG_OMN, LMG_EGY, LMG_KWT, LMG_QAT, LMG_BAH)      │
│   data flows: RMS → catalog/productdata → BLC orderItem.internalAttrs  │
│                        → sterling populator → OMS                      │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│  eFulfill / e-Fulfillment  (LMG e-comm fulfillment centres)            │
│   = the WMS-side "warehouse" nodes above + Sterling fulfillment ops    │
│   Flow:  BLC order ─► Sterling ─► OMS reserves ShipNode (DC/store/DS)  │
│          ─► WMS at that warehouse picks/packs ─► Shipping/Delivery     │
│          ─► status events ─► OMS ─► Sterling endpoint ─► BLC order     │
└────────────────────────────────────────────────────────────────────────┘

End-to-end order flow

1. Storefront places order → cart → cartops → order (BLC, source-system: BLC).
2. order emits an event; sterling service consumes it, calls LMGCreateOrderPopulator (uses CONCEPT_RMS_CODe for QCommerce dark-store ShipNode) → POSTs to IBM Sterling OMS at /eai/hybris/ordercreate.
3. Sterling also publishes order-submitted-event via lmgOrderSubmittedToWarehouseProducer → consumed by WMS at the resolved warehouse (CP_WAREHOUSE_OM, MAX_WAREHOUSE_KW, …).
4. WMS picks/packs at the eFulfill centre; status posted back to OMS.
5. OMS → BLC: OMS calls LMGOMSEndpoint / LMGOMSReturnEndPoint on the sterling service → BLC order updated (DefaultLMGOMSOrderUpdateService).
6. Cancellations: BLC publishes publish-cancellation-oms-event → sterling → OMS cancel URL (oms-cancel-ord7080).
7. Hybris path (legacy MaxFashion etc.) runs in parallel via hybris-integration for order lookup, refund options, addresses.
8. Returns / refund enquiry route through Kong (devapi3.landmarkgroup.com) — both BLC and OMS hit Kong-fro
9. RMS is the master for store/department/concept codes; that data lands in BLC catalog/productdata and travels on orderItem.internalAttributes so OMS and WMS can route to the right node.

Summary of who talks to whom

┌────────────────────┬──────────────────────────────────────────────────────────────────┬────────────────────────────────────┬─────────────────────────────┐
│       System       │                   BLC service that talks to it                   │             Dire Protocol           │
├────────────────────┼──────────────────────────────────────────────────────────────────┼────────────────────────────────────┼─────────────────────────────┤
│ IBM Sterling OMS   │ sterling                                                         │ bi-directional  afka                │
├────────────────────┼──────────────────────────────────────────────────────────────────┼────────────────────────────────────┼─────────────────────────────┤
│ SAP Hybris         │ hybris-integration                                               │ BLC → Hybris    uth)                │
├────────────────────┼──────────────────────────────────────────────────────────────────┼────────────────────────────────────┼─────────────────────────────┤
│ WMS (warehouses)   │ sterling, inventory-manager, inventory                           │ BLC → WMS (KafkaREST                │
├────────────────────┼──────────────────────────────────────────────────────────────────┼────────────────────────────────────┼─────────────────────────────┤
│ RMS                │ catalog, productdata (data feed)                                 │ RMS → BLC (masteort                 │
├────────────────────┼──────────────────────────────────────────────────────────────────┼────────────────────────────────────┼─────────────────────────────┤
│ eFulfill centres   │ sterling (via OMS ShipNode), shipping, delivery, collectionpoint │ downstream of OM + status callbacks │
├────────────────────┼──────────────────────────────────────────────────────────────────┼────────────────────────────────────┼─────────────────────────────┤
│ Kong retail API GW │ sterling, OMS                                                    │ both                                │
└────────────────────┴──────────────────────────────────────────────────────────────────┴────────────────────────────────────┴─────────────────────────────┘

Deep Dive: Kafka topics, OMS callbacks, RMS data feed

1) Kafka Topics — full producer/consumer map

▎ Source: spring.cloud.stream.bindings.* in sterling, orderops, notification, inventory-manager, cartops.
▎ All over Spring Cloud Stream / Kafka. group: = consumer group; destination: = Kafka topic.

Topic ↔ Service matrix

┌─────────────────────────────────────┬──────────────────────────────────────────────────────┬─────────────────────────────────────────────────────────────────────────────────────────────────┬───────────────────────────────────────────────┐
│                Topic                │                      Producers                       │                                            Consumers                                            │                    Purpose                    │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ orderCreated                        │ cartops (checkout)                                   │ sterling (sterlingOrderCreatedInput, grp sterling-order-created), notification (grp             │ Order placed in BLC → triggers OMS publish +  │
│                                     │                                                      │ cod-confirmed-sms-notification)                                                                 │ COD SMS                                       │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ order-submitted-event               │ sterling (lmgOrderSubmittedToWarehouseProducer)      │ orderops (lmgOrderSubmittedToWarehouseInput, grp sterling-order-submitted)                      │ After OMS create-order succeeds → WMS /       │
│                                     │                                                      │                                                                                                 │ downstream ops                                │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ publish-cancellation-oms-event      │ orderops (publishCancellationToOmsOutput)            │ sterling (sterlingOrderCancelledOutput/Input, grp sterling-order-cancelled), orderops           │ Forward BLC cancel → OMS cancel API           │
│                                     │                                                      │ (publishCancellationToOmsInput)                                                                 │                                               │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ fulfillmentCancelled                │ order                                                │ orderops (orderOperationsFulfillmentCancelledInput, grp order-operation-fulfillment-cancel, no  │ Trigger downstream cancel handling (refund,   │
│                                     │                                                      │ retry)                                                                                          │ notification)                                 │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ returnConfirmed                     │ order/orderops                                       │ orderops (grp order-operation-return-confirmed), notification (notification-return-confirmed,   │ Return confirmed by OMS → BLC return + emails │
│                                     │                                                      │ notification-return-confirmed-invoice)                                                          │                                               │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ returnCreated                       │ order                                                │ notification (notification-return-created)                                                      │ Return RMA created notification               │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ fulfillment-shipped-event           │ orderops                                             │ notification (notification-fulfillment-shipped)                                                 │ Shipment email                                │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ order-delivered-event               │ orderops (orderDeliveredOutput,                      │ notification (notification-fulfillment-delivered, notification-order-delivered)                 │ Delivery email/SMS + NPS feedback             │
│                                     │ virtualOrderFeedbackOutput)                          │                                                                                                 │                                               │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ order-outfordelivery-event          │ orderops                                             │ notification                                                                                    │ OFD notification                              │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ order-notdelivered-event            │ orderops                                             │ notification                                                                                    │ NDR notification                              │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ order-notpicked-event               │ orderops                                             │ notification                                                                                    │ Not-picked-up notification                    │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ order-collected-event               │ orderops                                             │ notification                                                                                    │ CnC collection done                           │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ order-readyForPickUp-email-event    │ orderops                                             │ notification (notification-ready-for-pickup-email)                                              │ CnC ready email                               │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ order-readyForPickUp-sms-event      │ orderops                                             │ notification (notification-ready-for-pickup-sms)                                                │ CnC ready SMS                                 │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ order-cancelled-event               │ orderops (orderCancelledOutput)                      │ downstream notification/refund                                                                  │ Customer-facing cancellation                  │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ virtual-fulfillment-delivered-event │ orderops                                             │ notification (grp lmg-virtual-fulfillment)                                                      │ Gift-card / virtual delivery                  │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ virtual-fulfillment-handler-event   │ orderops                                             │ orderops (virtualFulfillmentHandlerInput, grp gift-card-fulfillment)                            │ Internal virtual fulfillment loop             │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ gift-card-fulfillment-event         │ orderops (lmgGiftCardOrderFulfillmentCreatedOutput)  │ gift-card-fulfillment consumers                                                                 │ Issue gift card                               │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ refund-account-credit-event         │ orderops (lmgRefundAccountCreditOutput)              │ notification                                                                                    │ Refund credited email                         │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ cancel-refund-account-credit-event  │ orderops                                             │ notification                                                                                    │ Cancelled-refund email                        │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ update-bank-details-event           │ sterling (updateBankDetailsEmailOutput), orderops    │ notification (lmg-update-bank-details)                                                          │ Bank-detail change email                      │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ customer-verification-event         │ orderops (orderVerificationOutput)                   │ notification (lmg-customer-verification)                                                        │ KYC / verification                            │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ cod-otp-email-event                 │ orderops                                             │ notification (lmg-cod-otp-email)                                                                │ COD OTP email                                 │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ customer-otp-email-event            │ (customer)                                           │ notification (lmg-customer-otp-event)                                                           │ OTP login email                               │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ user-registration                   │ customer                                             │ notification (customer-user-activation, customer-welcome-email)                                 │ Welcome / activation                          │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ user-activation-resend              │ customer                                             │ notification (user-activation)                                                                  │ Resend activation                             │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ checkoutCompletion                  │ cart/cartops                                         │ notification (sms-notification)                                                                 │ Checkout SMS                                  │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ prideSubscriptionCreated            │ orion                                                │ notification (orion-order-created)                                                              │ Pride subscription created                    │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ shukran-sale-sms                    │ shukranloyalty                                       │ notification (lmg-shukran-sale-sms)                                                             │ Loyalty SMS                                   │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ payment-failed-event                │ paymenttransaction/orderops                          │ notification (notification-payment-failed)                                                      │ Payment failure email                         │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ account-deletion-event              │ customer                                             │ notification (notification-account-deletion)                                                    │ Account deletion email                        │
├─────────────────────────────────────┼──────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ triggeredJobEvent                   │ many (scheduledjob)                                  │ sterling (4 listeners), orderops (5 listeners)                                                  │ Generic retry / scheduled-job dispatcher —    │
│                                     │                                                      │                                                                                                 │ discriminated by group:                       │
└─────────────────────────────────────┴──────────────────────────────────────────────────────┴─────────────────────────────────────────────────────────────────────────────────────────────────┴───────────────────────────────────────────────┘

triggeredJobEvent retry/job groups

This one topic is reused as a retry bus; the consumer group selects the listener:

┌─────────────────────────────────────────┬──────────┬────────────────────────────────────────────────────────────────────────────────┐
│                  Group                  │ Service  │                                What it retries                                 │
├─────────────────────────────────────────┼──────────┼────────────────────────────────────────────────────────────────────────────────┤
│ sterling-order-created                  │ sterling │ Failed order publish to OMS (LMGRetryFailedMessageTriggeredJobListener)        │
├─────────────────────────────────────────┼──────────┼────────────────────────────────────────────────────────────────────────────────┤
│ sterling-rtlog-posting                  │ sterling │ Failed RT-log POST to OMS (LMGRetryFailedRTLogDataPostingTriggeredJobListener) │
├─────────────────────────────────────────┼──────────┼────────────────────────────────────────────────────────────────────────────────┤
│ order-address-translation-created       │ orderops │ Address-translation retries                                                    │
├─────────────────────────────────────────┼──────────┼────────────────────────────────────────────────────────────────────────────────┤
│ order-refund-event                      │ orderops │ Refund-confirm publish retries (max-attempts: 1, no DLQ)                       │
├─────────────────────────────────────────┼──────────┼────────────────────────────────────────────────────────────────────────────────┤
│ cnc-order-reminder-email-event          │ orderops │ CnC pickup reminder emails                                                     │
├─────────────────────────────────────────┼──────────┼────────────────────────────────────────────────────────────────────────────────┤
│ lmgShukranpay-cashbackorder-issue-event │ orderops │ Shukran cashback retry                                                         │
├─────────────────────────────────────────┼──────────┼────────────────────────────────────────────────────────────────────────────────┤
│ lmg-cancel-order-retry-event            │ orderops │ Cancel-order retry                                                             │
├─────────────────────────────────────────┼──────────┼────────────────────────────────────────────────────────────────────────────────┤
│ lmg-bank-account-details-purge-event    │ orderops │ Bank-details purge schedule                                                    │
└─────────────────────────────────────────┴──────────┴────────────────────────────────────────────────────────────────────────────────┘

Sterling Kafka block (canonical)

sterling/sterling-defaults.yml
bindings:
sterlingOrderCreatedInput      ← orderCreated                (in,  grp=sterling-order-created)
sterlingOrderCreatedOutput     → orderCreated                (out)
sterlingOrderCancelledOutput   → publish-cancellation-oms-event (out, grp=sterling-order-cancelled)
lmgOrderSubmittedToWarehouseProducer → order-submitted-event (out, grp=sterling-order-submitted)
updateBankDetailsEmailOutput   → update-bank-details-event
triggeredJobEventInputRetryOrderPublishFailure ← triggeredJobEvent (grp=sterling-order-created)
triggeredJobEventInputRetryRTLogDataPosting   ← triggeredJobEvent (grp=sterling-rtlog-posting)

---
2) OMS callback endpoints — what OMS POSTs back to BLC

All under sterling service. Base path /omswebservice/**. Methods marked @Policy(permissionRoots = {"OMS"}) are authz-gated to OMS service token.

LMGOMSEndpoint — order lifecycle callbacks (/omswebservice)

┌──────┬────────────────────────────────┬─────────────────────────────────────────────┬────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ HTTP │              Path              │                Caller intent                │                                                                 Downstream BLC action                                                                  │
├──────┼────────────────────────────────┼─────────────────────────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ POST │ /update-order-status?fields=…  │ Legacy status push (stub: always returns    │ no-op                                                                                                                                                  │
│      │                                │ success)                                    │                                                                                                                                                        │
├──────┼────────────────────────────────┼─────────────────────────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ POST │ /order-publish/acknowledgement │ OMS acks ordercreate (success/error code)   │ LMGOMSService.processAcknowledgement updates BLC Order                                                                                                 │
├──────┼────────────────────────────────┼─────────────────────────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ POST │ /oms-order-update              │ Shipping/picking status, status transitions │ LMGOrderService.updateOrderStatus(lmgOMSOrderData, …) — gated: PICKING_IN_PROGRESS / READY_FOR_PACKING only accepted for QCommerce apps                │
│      │                                │                                             │ (LMGQuickCommUtil.isQcApplication)                                                                                                                     │
├──────┼────────────────────────────────┼─────────────────────────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ POST │ /oms-order-cancellation        │ OMS-initiated cancel (e.g. inventory short) │ LMGOrderService.requestOrderCancel(request, ctx)                                                                                                       │
├──────┼────────────────────────────────┼─────────────────────────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ POST │ /updatePOS                     │ OMS updates point-of-service / store        │ LMGOMSService.updatePOS writes to inventory locations                                                                                                  │
│      │                                │ metadata                                    │                                                                                                                                                        │
├──────┼────────────────────────────────┼─────────────────────────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ POST │ /uncollect-order               │ Mark CnC order uncollected                  │ LMGOrderService.markUncollectOrder                                                                                                                     │
└──────┴────────────────────────────────┴─────────────────────────────────────────────┴────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

LMGOMSReturnEndPoint — returns & refunds (/omswebservice/return)

┌──────┬───────────────────────────────────────────────────────────────┬───────────────────────────────────────────────────────────────────────────────────────┬──────────────────────────────────────────────────────────┐
│ HTTP │                             Path                              │                                       Direction                                       │                          Action                          │
├──────┼───────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────────────────┤
│ POST │ /create-update                                                │ OMS → BLC                                                                             │ Create / update return + refund options (processRequest) │
├──────┼───────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────────────────┤
│ POST │ /return-refund-enquiry/{orderNumber}                          │ BLC admin / OMS callback                                                              │ Initiate return, get refund options                      │
├──────┼───────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────────────────┤
│ GET  │ /return-policy-enquiry/{orderNumber}                          │ BLC admin                                                                             │ Fetch return policy from OMS                             │
├──────┼───────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────────────────┤
│ POST │ /refund-confirm-endpoint/{orderNumber}                        │ BLC → OMS push of refund-confirmation (this endpoint also receives confirmation back) │ Sets Updated return refund published flag                │
├──────┼───────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────────────────┤
│ GET  │ /order/{orderId}/return/{returnAuthorizationId}/cancel-return │ BLC admin                                                                             │ Cancel return                                            │
├──────┼───────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────┼──────────────────────────────────────────────────────────┤
│ POST │ /{orderNumber}/omsRmaResponse                                 │ OMS callback with RMA + pickup addr/refund details                                    │ getReturnWithRma writes RMA info on BLC return           │
└──────┴───────────────────────────────────────────────────────────────┴───────────────────────────────────────────────────────────────────────────────────────┴──────────────────────────────────────────────────────────┘

LMGOMSCostCallEndPoint (/omswebservice/consignment)

- POST /get-consignment-details — OMS cost call for consignment details (real-time freight quote).

LMGInvoiceEndPoint (/omswebservice/invoice)

- GET /getDeliveryNote — OMS pulls delivery note PDF from BLC.

Outbound BLC → OMS / Hybris (the other direction)

From sterling-defaults.yml:

┌──────────────────────────────────┬──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│           BLC outbound           │                                                             URL                                                              │
├──────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Create order                     │ POST http://161.156.104.9:7080/eai/hybris/ordercreate (basic auth: ORDCRT_TST)                                               │
├──────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Cancel order                     │ POST http://161.156.104.9:7080/eai/hybris/ordercancellation (user ORDCAN_TST)                                                │
├──────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Cancel from oms-cancel-order-url │ ${oms-cancel-order-url} = same 161.156.104.9:7080 host                                                                       │
├──────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Return policy enquiry            │ https://apidev.landmarkgroup.com/landmarkgroup/retail-sit/v3/customer-orders/{orderCode}/events/return-policy-enquiry        │
├──────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Returns refund processed         │ …/events/returns-refund-processed                                                                                            │
├──────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Refund-enquiry (GCC)             │ https://apidev.landmarkgroup.com/.../events/v2/return-refund-enquiry                                                         │
├──────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Cancel return                    │ /landmarkgroup/retail-sit/v3/customer-return-orders                                                                          │
├──────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Kong-fronted variants            │ https://devapi3.landmarkgroup.com/customer-orders, /customer-return-orders                                                   │
├──────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ OrderOps (BLC-internal)          │ https://localhost:8470/orders, /fulfillment-operations, /order-fulfillments, /generate-invoice-number, /rt-logs/rt-log-retry │
└──────────────────────────────────┴──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

End-to-end OMS round-trip

BLC order placed
└─► Kafka: orderCreated
└─► sterling.LMGSterlingOrderCreatedListener
└─► POST http://161.156.104.9:7080/eai/hybris/ordercreate (Sterling OMS / Hybris EAI)
│  (failure → triggeredJobEvent grp=sterling-order-created → retry listener)
▼
OMS asynchronously calls back:
① POST /omswebservice/order-publish/acknowledgement   (ack create)
② POST /omswebservice/oms-order-update                (picking → packed → shipped …)
③ POST /omswebservice/oms-order-cancellation          (OMS-initiated cancel)
④ POST /omswebservice/return/create-update            (return raised)
⑤ POST /omswebservice/return/{order}/omsRmaResponse   (RMA + pickup addr)
⑥ POST /omswebservice/consignment/get-consignment-details (cost call)
⑦ GET  /omswebservice/invoice/getDeliveryNote
⑧ POST /omswebservice/updatePOS                       (store metadata sync)
⑨ POST /omswebservice/uncollect-order                 (CnC abandoned)
│
▼
sterling writes back to BLC `order` via:
- DefaultLMGOMSOrderUpdateService.updateFulfillmentStatus
- DefaultLMGOMSShortPickService (short-pick handling)
- DefaultLMGGCCReturnUpdateService (returns)
│
▼
sterling re-emits to Kafka:
- lmgOrderSubmittedToWarehouseProducer → order-submitted-event (→ WMS via orderops)
- sterlingOrderCancelledOutput → publish-cancellation-oms-event (cancel fan-out)
- updateBankDetailsEmailOutput → update-bank-details-event

---
3) RMS → catalog data feed path

There is no direct service-to-service link to Oracle RMS. RMS data lands in BLC via the import service (CSV uploads) and then propagates as internalAttributes on cart/order items so OMS can route to the right node.

Where RMS codes live in BLC

┌──────────────────┬───────────────────────────────────────────────┬───────────────────────────────────────────────────────────────────┬─────────────────────────────────────────────────────────┐
│      Layer       │                 Class / table                 │                               Field                               │                         Source                          │
├──────────────────┼───────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────┤
│ Catalog domain   │ catalog/.../domain/LMGConcept.java            │ rmsCode                                                           │ per-concept RMS code                                    │
├──────────────────┼───────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────┤
│ Catalog JPA      │ catalog/.../LMGJpaConcept.java                │ RMS_CODE column on LMG_CONCEPT table                              │ persisted store                                         │
├──────────────────┼───────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────┤
│ CC integration   │ lmg-catalog-ccintegration/.../LMGConcept.java │ rmsCode                                                           │ DTO mirroring catalog                                   │
├──────────────────┼───────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────┤
│ Order item       │ cart/order OrderItem.internalAttributes       │ conceptRmsCode (key CONCEPT_RMS_CODE)                             │ populated at cart → order from product → concept lookup │
├──────────────────┼───────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────┤
│ Fulfillment      │ OrderFulfillment.internalAttributes           │ rmsStoreCode (key RMS_STORE_CODE), used for QCommerce dark stores │ set when QCommerce fulfillment option selected          │
├──────────────────┼───────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────┤
│ Delivery service │ delivery/.../LMGJpaArea + LMGAreaDetails      │ warehouseCode                                                     │ from LMG_AREA.csv (also has rmsStoreCode)               │
├──────────────────┼───────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────┼─────────────────────────────────────────────────────────┤
│ Inventory loc    │ inventory/.../LMGSkuInventoryShopEndpoint     │ warehouseCode query param                                         │ reads inventory keyed on warehouse / RMS store          │
└──────────────────┴───────────────────────────────────────────────┴───────────────────────────────────────────────────────────────────┴─────────────────────────────────────────────────────────┘

Concept × country / web-store catalog (sterling-defaults.yml)

┌──────────────────┬───────────────┬───────────────────────┬──────────────┐
│     Concept      │ RMS / country │    enterprise-code    │ web-store-id │
├──────────────────┼───────────────┼───────────────────────┼──────────────┤
│ MX (Max Fashion) │ EG            │ LMG_EGY               │ 88882        │
├──────────────────┼───────────────┼───────────────────────┼──────────────┤
│ MX               │ OM            │ LMG_OMN               │ 88880        │
├──────────────────┼───────────────┼───────────────────────┼──────────────┤
│ MX               │ KW            │ LMG_KWT               │ 88883        │
├──────────────────┼───────────────┼───────────────────────┼──────────────┤
│ MX               │ BH            │ LMG_BAH               │ 88884        │
├──────────────────┼───────────────┼───────────────────────┼──────────────┤
│ MX               │ QA            │ LMG_QAT               │ 88881        │
├──────────────────┼───────────────┼───────────────────────┼──────────────┤
│ CP (Centrepoint) │ OM/KW/…       │ LMG_OMN / LMG_KWT / … │ per country  │
├──────────────────┼───────────────┼───────────────────────┼──────────────┤
│ HC (Home Centre) │ OM/EG/KW      │ per country           │ per country  │
├──────────────────┼───────────────┼───────────────────────┼──────────────┤
│ HB (HomeBox)     │ —             │ —                     │ —            │
└──────────────────┴───────────────┴───────────────────────┴──────────────┘

The CSV pipeline (RMS as upstream system-of-record)

Oracle RMS (master)
│  (export jobs — outside this repo, typically nightly)
▼
CSV drops consumed by `import` service:
import/src/main/resources/custom-import-examples/
├─ LMG_AREA.csv            (id, isocode, warehouseCode, rmsStoreCode, deliveryZone …)
├─ LMG_BRAND.csv
├─ LMG_CATEGORY_DETAILS.csv, LMG_CATEGORY_FACETS.csv, Category.csv
├─ LMG_CROSS_CONCEPT_PRODUCTS.csv
├─ LMG_DELIVERY_ESTIMATION.csv  (areacode, cutoffs, returnTAT, storeTAT …)
├─ LMG_PRODUCT_*.csv  (ADDONS, LINKING_DIMENSIONS, RANKING, REFERENCE)
├─ LMG_PROMOTION.csv, LMG_UPDATE_PROMOTION.csv, LMG_CAMPAIGN.csv
├─ LMG_SEO_*.csv, LMG_SIZE_*.csv
└─ ADVANCED_TAG.csv, CATEGORY_TO_PRODUCT.csv
│
▼
import service writes to BLC databases via:
• catalog       (Concept, Product, Category, SizeGuide, Brand)
• productdata   (extended product attrs)
• offer / campaign / pricing (promos)
• delivery      (LMGJpaArea — warehouseCode, rmsStoreCode, deliveryZone)
• inventory / inventory-manager (warehouse-mapping config — DG_*_WAREHOUSE_*)
│
▼
At runtime, the values become attributes on cart/order items:

catalog.LMGConcept.rmsCode  ───►  conceptRmsCode  on OrderItem.internalAttributes
│
▼
sterling.LMGOrderPopulatorHelper.getDepartmentCode()
│
▼
<DepartmentCode> in OMS create-order XML
+ <ShipNode> = rmsStoreCode (QCommerce dark store)

delivery.LMGJpaArea.rmsStoreCode  ──►  delivery picks store/warehouse during quote
delivery.LMGJpaArea.warehouseCode ──►  inventory locationNumber lookup
│
▼
fashion-inventory.DefaultLMGFashionSkuInventoryService
.readByLocationNumber(rmsCode, ctx)   ←  rmsCode used as locationId

Where the BLC code consumes rmsCode / rmsStoreCode

┌──────────────────────────────────────────────────────────────────┬───────────────────────────────────────────────────────────────────────────────────────────────────┐
│                               File                               │                                           What it does                                            │
├──────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────┤
│ sterling/.../LMGOrderPopulatorHelper.java:182,582                │ Pulls CONCEPT_RMS_CODE from OrderItem.internalAttributes, uses as DepartmentCode in OMS order XML │
├──────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────┤
│ sterling/.../LMGOrderPopulatorHelper.java:80,1686                │ RMS_STORE_CODE attr on fulfillment → <ShipNode> for QCommerce dark store routing                  │
├──────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────┤
│ sterling/.../domain/LMGLocalizedRegionArea.java:51               │ DTO field rmsStoreCode for area→store mapping                                                     │
├──────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────┤
│ fashion-inventory/.../DefaultLMGFashionSkuInventoryService       │ readByLocationNumber(rmsCode, ctx) — RMS code is the inventory location key                       │
├──────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────┤
│ delivery/.../LMGJpaArea.java:67                                  │ warehouseCode (from LMG_AREA.csv) drives delivery routing                                         │
├──────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────┤
│ inventory/.../LMGSkuInventoryShopEndpoint.java:121,164           │ @RequestParam warehouseCode for stock lookups                                                     │
├──────────────────────────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────────────┤
│ flexpackages/balanced/browse/.../application-default.yml:837–861 │ Static concept→rmsCode table (4, 35, 29, 5, 2, 1, 3, 6, 17) — fallback when DB row missing        │
└──────────────────────────────────────────────────────────────────┴───────────────────────────────────────────────────────────────────────────────────────────────────┘

So how RMS connects to OMS / WMS / eFulfill

RMS ────CSV────► import svc ────► catalog (LMG_CONCEPT.RMS_CODE)
└──► delivery (LMG_AREA.rmsStoreCode, warehouseCode)
└──► inventory-manager (dg-warehouse-mapping)

At checkout / order submit:
catalog.LMGConcept.rmsCode   ──►  OrderItem.conceptRmsCode
fulfillment.rmsStoreCode      ──►  OrderFulfillment.rmsStoreCode
delivery area.warehouseCode  ──►  inventory location key

sterling consumes these and ships to OMS:
<Order>
<OrderLines>
<OrderLine>
<DepartmentCode>{conceptRmsCode}</DepartmentCode>
<ShipNode>{rmsStoreCode}</ShipNode>     ← for QCommerce
<ItemDetails LocationNumber="{warehouseCode}" .../>
</OrderLine>
</OrderLines>
</Order>
│
▼
IBM Sterling OMS resolves ShipNode → physical WMS warehouse
(CP_WAREHOUSE_OM, MAX_WAREHOUSE_KW, HOMECENTRE_WAREHOUSE_OM, …)
│
▼ Kafka `order-submitted-event` (sterling → orderops)
eFulfill warehouse picks/packs → status pushed back via /omswebservice/oms-order-update

Net: who talks to whom

                   ┌──────────────┐
                   │  Oracle RMS  │ ──CSV nightly──► import service ──► catalog / delivery / inventory
                   └──────────────┘                                          │
                                                                             │ runtime
┌───────────────────────────────────────────────────────────────┐         │
│ Storefront → cart → cartops → order → orderops                │◄────────┘
│                                  │                            │
│                                  ▼ Kafka orderCreated         │
│                              sterling                         │
│                                  │                            │
│              ┌───────────────────┼────────────────┐           │
│              ▼                   ▼                ▼           │
│   POST /eai/hybris/ordercreate   │      Kafka order-submitted-event
│   (IBM Sterling OMS @            │              │             │
│    161.156.104.9:7080)           │              ▼             │
│              │                   │           WMS / eFulfill   │
│              ▼                   │           (CP_WAREHOUSE_*  │
│   OMS callbacks → /omswebservice/*  MAX_WAREHOUSE_* …)        │
│   (ack, order-update, return,    │              │             │
│    consignment, invoice, POS,    │              ▼             │
│    uncollect)                    │       status events ───────┘
│              │                   │
│              ▼                   ▼
│   sterling updates BLC order, fires Kafka delivery/refund/return events
│              │
│              ▼
│   notification service fans out → SMS/email/Gupshup/Vectramind
└────────────────────────────────────────────────────────────────┘

Want me to keep going — e.g., trace one specific event end-to-end (say order-submitted-event to WMS pickup status), or pull the DLQ / retry semantics for a particular topic?
