# cHAZe - Personal Shopper

An Android shopping assistant that compares grocery prices across Swiss retailers (Migros, Coop, Denner, Aldi, Lidl) and finds the cheapest or closest store for your shopping basket.

---

## Prerequisites

- Android Studio Ladybug (2024.2.1) or newer
- JDK 11+
- Android SDK 35 (minimum SDK 24)
- A Google Cloud account
- A Firebase project

---

## Required API Keys

### Google API Key

Go to [Google Cloud Console](https://console.cloud.google.com) and enable:
- Maps SDK for Android
- Distance Matrix API
- Places API

### Firebase Setup

Go to [Firebase Console](https://console.firebase.google.com) and enable:
- Authentication (Email/Password provider)
- Firestore Database
- Vertex AI (for the chatbot)

---

## Installation

### 1. Clone the repository

```bash
git clone https://gitlab.epfl.ch/android-ee-490/2025-2026/project-1s-chaze-personal-shopper.git
cd project-1s-chaze-personal-shopper
```

### 2. Configure the Google API Key

Create a file named `local.properties` in the project root (if it doesn't exist) and add:

```properties
sdk.dir=/path/to/your/Android/sdk
GOOGLE_API_KEY=your_google_api_key_here
```

**Do not commit this file to version control.**

### 3. Add Firebase Configuration

Download `google-services.json` from your Firebase project and place it in:

```
mobile/google-services.json
```

### 4. Build and Run

```bash
./gradlew assembleDebug
```

Or open the project in Android Studio and click Run.

---

## Database Setup

The app requires Firestore to be populated with product and store data.

### Firestore Structure

```
products/
    {productId}
        name: string
        category: string
        product_type: string
        image_url: string
        keywords: array

retailer/
    {retailerId}
        name: string
        logo_url: string
        products_per_retailer/
            {documentId}
                price: number
                product_type: string
                product_name: string
                unit: string
                in_stock: boolean
                has_discount: { discounted_price: number }

stores/
    {storeId}
        name: string
        retailer_id: string
        latitude: number
        longitude: number
        address: string

users/
    {userId}
        displayName: string
        email: string
        preferences: map
        baskets/
        past_baskets/
```

### Seeding Data

JSON files are provided in `cHAZe_data_JSON/`:

- `firestore_products.json` - Product catalog
- `firestore_retailers.json` - Retailer information
- `firestore_stores.json` - Store locations
- `firestore_retailer_prices.json` - Prices per retailer
- `firestore_discounts.json` - Discount data

To upload:

```bash
cd cHAZe_data_JSON
python transform_to_firestore.py
```

---

## Project Structure

```
mobile/src/main/java/com/epfl/esl/chaze/
├── data/
│   ├── model/          # Data classes
│   └── repository/     # Firebase access
├── features/
│   ├── analytics/      # Spending charts
│   ├── basket/         # Basket management
│   ├── chatbot/        # AI assistant
│   ├── home/           # Home screen
│   ├── localization/   # Address management
│   ├── login/          # Authentication
│   ├── search/         # Product search
│   ├── shopping/       # Shopping lists
│   └── user/           # Profile and settings
├── navigation/         # Navigation graph
├── notifications/      # Background workers
├── services/           # Optimization and Maps services
├── ui/                 # Shared components
├── utils/              # Utilities
└── widget/             # Home screen widget
```

---

## Using the App

### First Launch

1. Open the app and create an account with email/password, or sign in if you already have one (IMPORTANT NOTICE: Please do not forget your password as there is no "Reset Password" option)
2. After login, go to the **Profile** tab to set your preferences:
   - Choose your transport mode (walking, cycling, transit, or driving)
   - Set your maximum search distance (1-20 km)

### Adding Products to Your Basket

There are two ways to add products:

**Option 1: Search**
- Go to the **Search** tab
- Type a product name (e.g., "milk", "apples", "bread")
- Browse the results and swipe the product to add to the basket
**Option 2: Chatbot**
- Go to the **Chatbot** tab
- Type naturally, for example:
  - "Add apples to my current basket"
- The assistant will confirm what was added

The procuts you can look for are: chicken, rice, pasta, bread, tomato, carrot, potato, cheese, orange, apple, banana, toilet paper, laundry detergent, egg, milk

Many of those have "bio" variants

### Finding the Best Store

Once you have items in your basket:

1. Go to the **Basket** tab
2. Review your items (swipe left to remove any)
3. Tap the Validate Basket button
4. The app shows you the best store with:
   - Store name and retailer
   - Total basket price
   - Distance and travel time
   - Money you saved taking discounts into account
   - Mark baskets as favorites for quick access

You can also ask the chatbot: "Find the cheapest store to buy" a given product

### Managing Past Baskets

- Go to the **Home** tab to see your shopping history
- Tap on any past basket to view its contents
- Use "Add to Current Basket" to reload items for a repeat shopping trip

### Viewing Discounts

- Tap the bell icon in the top header
- See current discounts
- The app monitors prices in the background and notifies you of deals

### Viewing Your Analytics

- See charts showing:
  - Monthly spending over time
  - Spending by product type
  - Total savings from discounts

---

## License

Developed as part of EE-490 Android Development at EPFL.
