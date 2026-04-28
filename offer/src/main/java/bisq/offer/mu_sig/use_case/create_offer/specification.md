## Specification for the create offer process

### Direction

The user can select **BUY** or **SELL**.

Terminology:

* `displayDirection` is the direction shown to the maker in the create-offer UI.
* `offerDirection` is the direction stored in the offer and is always expressed from the Bitcoin side.
* For Bitcoin-Fiat markets, `displayDirection == offerDirection`.
* For Altcoin-Bitcoin markets, `offerDirection == displayDirection.mirror()`.

Examples:

* In Bitcoin-Fiat markets, `displayDirection` **BUY** means buying Bitcoin with Fiat.
* In Altcoin-Bitcoin markets, `displayDirection` **BUY** means buying Altcoin with Bitcoin. The stored `offerDirection` is therefore **SELL** from the Bitcoin side.

For takers, the direction is mirrored from the maker offer direction. A taker who wants to buy Bitcoin from a Fiat offer is looking for maker offers with direction **SELL**.

The selected direction impacts the **user-specific trade amount limit** only when `offerDirection` is **BUY** in a Bitcoin-Fiat market.

---

### Market

The user can select either a **Bitcoin-Fiat market** or an **Altcoin-Bitcoin market**.

The selected market affects:

* available payment methods
* default price quote
* trade amount limits
* whether a user-specific trade amount limit can apply

---

### Payment method

The user can select one or more payment methods.

* If no account exists for a selected payment method, a popup is shown prompting the user to create one. The method cannot be selected until an account exists.
* If multiple accounts exist, a dropdown is shown to choose the desired account.
* If exactly one account exists for the selected payment method, no dropdown is displayed.
* If exactly one market-eligible account exists in total, its payment method and account are preselected.

The user can select up to **4 payment methods** (as defined by `CreateOfferPaymentMethodUseCase.MAX_NUM_PAYMENT_METHODS`).

Payment method selection affects the payment-rail based maximum trade amount. If multiple payment methods are selected, the method with the lowest maximum amount determines the effective payment-rail maximum.

The user-specific trade amount limit is independent of the selected payment method, but it can further reduce the effective maximum in Bitcoin-Fiat buy offers.

---

### Price

The user can define the offer price using either:

* a **floating price** (percentage based on market price), or
* a **fixed price**

Input can be done via:

* text input
* slider

Both inputs are synchronized.

Allowed floating-price percentage range: **-10% to +50%**.

Changing the price affects:

* trade amount limits
* trade amounts

The entered amount is treated as the **stable value**, while price changes are applied to the **passive value**.

---

### Amount

The user can choose:

* **fixed amount** or **range amount**
* If input controls are using the **Bitcoin side** or the **non-Bitcoin side**

Input methods:

* text input
* slider

The **passive amount** (counterpart of the active amount) is calculated automatically based on the selected price quote.

Constraints:

* Minimum and maximum allowed amounts are determined by the payment method with the **highest chargeback risk**
* An optional **user-specific trade amount limit** may further reduce the maximum

---

#### Trade amount limits

Trade amount limits define the minimum and maximum allowed trade size.

* Limits are determined by the payment method with the **highest chargeback risk**
* They are calculated based on:

    * selected payment methods
    * market price
    * offer price quote

If no payment method is selected yet, the unrestricted system maximum is used.

All limits are internally defined in **USD** to reduce the impact of market volatility.

Conversion rules:

* **Bitcoin-Fiat markets**:
  USD → Fiat using market price (Fiat is the stable reference)
  Bitcoin amount is adjusted using the offer price

* **Altcoin-Bitcoin markets**:
  USD → Bitcoin (Bitcoin is the stable reference)
  Altcoin amount is adjusted using the offer price

Bounds:

* Minimum: **10 USD**
* Maximum: **10,000 USD**

Depending on the payment method, the maximum may be reduced by up to **50%** (i.e., down to **5,000 USD**).

---

#### User-specific trade amount limit

For **Bitcoin buyers in Bitcoin-Fiat markets**, an additional limit based on reputation data is applied.

* Not applied to Altcoin-Bitcoin markets
* Not applied to Bitcoin-Fiat sell offers
* Can reduce the maximum allowed trade amount

Rules:

* If between the minimum and the system maximum, it becomes the effective maximum.
* If above the system maximum, it does not reduce the effective maximum.
* If below the minimum, it is clamped to the minimum, so the effective range collapses to the minimum amount.

Summary:

* Only affects **Bitcoin buyers in Bitcoin-Fiat markets**
* Always reduces (never increases) the allowed trade amount

UI behavior:

* The full trade range (e.g., **10 USD – 5,000 USD**) is shown as the slider range
* A **restricted slider track** indicates the user-specific limit
* Slider interaction is capped at that limit

Additionally:

* Contextual information is shown in the UI
* A detailed explanation can be opened in an overlay view
