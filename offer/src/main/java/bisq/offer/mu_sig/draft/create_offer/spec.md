## Handles the interaction between multiple aspects of the offer creation process

### Direction

The user can select **BUY** or **SELL**.
For Bitcin-Fiat markets the BUY direction means buying Bitcoin with Fiat. 
For Altcoin-Bitcoin market the BUY direction means buying Altcoin with Bitcoin.
The direction shown in the UI is called the displayDirection. 
Internally the direction is always referring to the Bitcoin side, thus for altcoins the direction is flipped to the one displayed in the UI.
Furthermore, for the taker the offers direction is mirrored. A taker who wants to buy Bitcoin from a Fiat offer is looking for offers with direction SELL.

The selected direction impacts the **user-specific trade amount limits**, which are applied only for buyers.

---

### Market

The user can select either a **Bitcoin–Fiat market** or an **Altcoin–Bitcoin market**.

The selected market affects:

* available payment methods
* default price quote
* trade amount limits
* user-specific trade amount limits (only Fiat markets are affected by user-specific trade amount limits)

---

### Payment method

The user can select one or more payment methods.

* If no account exists for a selected payment method, a popup is shown prompting the user to create one. The method cannot be selected until an account exists.
* If multiple accounts exist, a dropdown is shown to choose the desired account.
* If exactly one account exists for a payment method no dropdown is displayed.
* If the user has exactly one payment method with one account, it is preselected.

The user can select up to **5 payment methods**.

Payment method selection impacts the **user-specific trade amount limits** in case of Fiat markets.

---

### Price

The user can define the price using either:

* a **floating price** (percentage based on market price), or
* a **fixed price**

Input can be done via:

* text input
* slider

Both inputs are synchronized.

Allowed price range: **-10% to +50%**.

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

All limits are internally defined in **USD** to reduce the impact of market volatility.

Conversion rules:

* **Bitcoin–Fiat markets**:
  USD → Fiat using market price (Fiat is the stable reference)
  Bitcoin amount is adjusted using the offer price

* **Altcoin–Bitcoin markets**:
  USD → Bitcoin (Bitcoin is the stable reference)
  Altcoin amount is adjusted using the offer price

Bounds:

* Minimum: **10 USD**
* Maximum: **10,000 USD**

Depending on the payment method, the maximum may be reduced by up to **50%** (i.e., down to **5,000 USD**).

---

#### User-specific trade amount limit

For **Bitcoin buyers in Fiat markets**, an additional limit based on reputation data is applied.

* Not applied to Altcoin markets
* Can reduce the maximum allowed trade amount

Rules:

* If below the system max → becomes the effective max
* If above the system max → clamped to the system max
* If below the minimum → clamped to the minimum

Summary:

* Only affects **buyers in Fiat markets**
* Only effective if within the global min/max range
* Always reduces (never increases) the allowed trade amount

UI behavior:

* The full trade range (e.g., **10 USD – 5,000 USD**) is shown as the slider range
* A **restricted slider track** indicates the user-specific limit
* Slider interaction is capped at that limit

Additionally:

* Contextual information is shown in the UI
* A detailed explanation can be opened in an overlay view
