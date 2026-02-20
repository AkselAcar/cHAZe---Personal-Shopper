# These files were generated using AI assistance.

"""
Transform cHAZe JSON files to Firestore-optimized format.
Phase 1: Add keyword search to products.
"""
import json
import re
from pathlib import Path
from unicodedata import normalize

# Define input and output file paths
BASE_DIR = Path(__file__).parent # base directory of this script
IN_DIR = BASE_DIR / 'JSON files before firestrore format'
IN_PRODUCTS = IN_DIR / 'cHAZe_global_products.json'
IN_STORES = IN_DIR / 'cHAZe_stores.json'
IN_STORE_PRICES = IN_DIR / 'cHAZe_store_prices.json'

# Define output files
OUT_PRODUCTS = BASE_DIR / 'firestore_products.json'
OUT_STORES = BASE_DIR / 'firestore_stores.json'
OUT_STORE_PRICES = BASE_DIR / 'firestore_store_prices.json'
OUT_RETAILERS = BASE_DIR / 'firestore_retailers.json'
OUT_DISCOUNTS = BASE_DIR / 'firestore_discounts.json'
OUT_RETAILER_PRICES = BASE_DIR / 'firestore_retailer_prices.json'
IN_PRODUCT_KEYWORDS = BASE_DIR / 'firestore_product_keywords.json'

# -------- PHASE 0: Retailers/Chains --------
# This phase creates a retailers collection with known chains.

def extract_retailer_id(store_name: str) -> str:
    """Extract retailer ID from store name."""
    if not store_name:
        return 'unknown'
    
    name_lower = store_name.lower().strip() # normalize case and trim spaces
    
    # Map common variations to canonical IDs
    if 'migros' in name_lower:
        return 'migros'
    elif 'coop' in name_lower:
        return 'coop'
    elif 'denner' in name_lower:
        return 'denner'
    elif 'aldi' in name_lower:
        return 'aldi'
    elif 'aligro' in name_lower:
        return 'aligro'
    else:
        return 'other'


def create_retailers():
    """Phase 0: Create retailers/chains collection."""
    retailers = [
        {
            'retailer_id': 'migros',
            'name': 'Migros',
            'logo_url': 'https://upload.wikimedia.org/wikipedia/commons/thumb/b/b9/Migros-Logo.svg/320px-Migros-Logo.svg.png',
            'website': 'https://www.migros.ch',
            'description': 'Switzerland\'s largest supermarket chain',
            'primary_color': '#FF6600',
            'categories': ['Supermarket', 'Hypermarket']
        },
        {
            'retailer_id': 'coop',
            'name': 'Coop',
            'logo_url': 'https://upload.wikimedia.org/wikipedia/commons/thumb/2/26/Coop_Logo.svg/320px-Coop_Logo.svg.png',
            'website': 'https://www.coop.ch',
            'description': 'Switzerland\'s second largest supermarket chain',
            'primary_color': '#E30613',
            'categories': ['Supermarket', 'Hypermarket']
        },
        {
            'retailer_id': 'denner',
            'name': 'Denner',
            'logo_url': 'https://upload.wikimedia.org/wikipedia/commons/thumb/1/1f/Denner_Logo.svg/320px-Denner_Logo.svg.png',
            'website': 'https://www.denner.ch',
            'description': 'Swiss discount supermarket chain',
            'primary_color': '#D70926',
            'categories': ['Discount Supermarket']
        },
        {
            'retailer_id': 'aldi',
            'name': 'Aldi',
            'logo_url': 'https://upload.wikimedia.org/wikipedia/commons/thumb/4/4e/Aldi_Sud_logo.svg/320px-Aldi_Sud_logo.svg.png',
            'website': 'https://www.aldi-suisse.ch',
            'description': 'International discount supermarket chain',
            'primary_color': '#009FE3',
            'categories': ['Discount Supermarket']
        },
        {
            'retailer_id': 'aligro',
            'name': 'Aligro',
            'logo_url': 'https://www.aligro.ch/themes/custom/aligro/logo.svg',
            'website': 'https://www.aligro.ch',
            'description': 'Swiss cash-and-carry and wholesale food supplier',
            'primary_color': '#E30613',
            'categories': ['Cash & Carry', 'Wholesale']
        },
        {
            'retailer_id': 'other',
            'name': 'Other',
            'logo_url': None,
            'website': None,
            'description': 'Other retailers',
            'primary_color': '#808080',
            'categories': ['Various']
        }
    ]
    
    with OUT_RETAILERS.open('w', encoding='utf-8') as f:
        json.dump(retailers, f, ensure_ascii=False, indent=2)
    
    print(f'✓ Phase 0 complete: {OUT_RETAILERS.name}')
    print(f'  Created {len(retailers)} retailer entries')
    
    # Show samples
    print(f'\n  Retailers: {", ".join([r["name"] for r in retailers])}')

# -------- PHASE 1: Clean Special Characters --------

def clean_special_chars(text: str) -> str:
    """Replace problematic special characters like Œ with ASCII equivalents."""
    if not text:
        return ''
    # Replace special ligatures and characters
    replacements = {
        'Œ': 'Oe',  # Capital Œ
        'œ': 'oe',  # Lowercase œ
    }
    cleaned = text
    for char, replacement in replacements.items():
        cleaned = cleaned.replace(char, replacement)
    return cleaned

def normalize_text(text: str) -> str:
    """Remove accents and normalize unicode."""
    if not text:
        return ''
    # First clean special characters
    text = clean_special_chars(text)
    # NFD decomposition then remove combining marks
    # NFD decomposes characters into base + accents, e.g., é -> e +  ́
    nfd = normalize('NFD', text)
    return ''.join(c for c in nfd if not re.match(r'[\u0300-\u036f]', c)) # remove combining marks

def transform_products():
    """Phase 1: Clean special characters and apply keywords from mapping file.

    Notes:
    - Keywords are loaded from `firestore_product_keywords.json`. Edit that file directly if you want to
      maintain or override keywords manually before running this script.
    - If no keywords file is present an empty keyword list will be used for each product.
    """
    if not IN_PRODUCTS.exists():
        raise FileNotFoundError(f'{IN_PRODUCTS} not found')
    
    # Load keywords mapping (optional - will use empty list if not found)
    # You can edit `firestore_product_keywords.json` manually to override keywords for products.
    keywords_map = {}
    if IN_PRODUCT_KEYWORDS.exists():
        with IN_PRODUCT_KEYWORDS.open('r', encoding='utf-8') as f:
            keywords_map = json.load(f)
        print(f'  Loaded keywords for {len(keywords_map)} products from {IN_PRODUCT_KEYWORDS.name}')
    else:
        print(f'  Keywords file not found: {IN_PRODUCT_KEYWORDS.name} (will use empty keywords)')
    
    with IN_PRODUCTS.open('r', encoding='utf-8') as f:
        products = json.load(f)
    
    # Process products
    for product in products:
        # Clean special characters from name and category
        if 'name' in product and product['name']:
            product['name'] = clean_special_chars(product['name'])
        if 'category' in product and product['category']:
            product['category'] = clean_special_chars(product['category'])
        
        # Apply keywords from mapping file, or empty list if not found
        product_id = product.get('product_id')
        if product_id and product_id in keywords_map:
            product['keywords'] = keywords_map[product_id]
        else:
            product['keywords'] = []
        
        # Preserve product_type if it exists (from cHAZe_global_products)
        # No need to do anything special, it will be preserved automatically
    
    #
    with OUT_PRODUCTS.open('w', encoding='utf-8') as f:
        json.dump(products, f, ensure_ascii=False, indent=2)
    
    print(f' Phase 1 complete: {OUT_PRODUCTS.name}')
    print(f'  Transformed {len(products)} products (keywords from mapping file)')
    
    # Count products with product_type
    with_type = sum(1 for p in products if p.get('product_type'))
    print(f'  Products with product_type: {with_type}/{len(products)}')
    
    # Show sample
    if products:
        sample = products[0]
        print(f'\n  Sample: {sample.get("name", "?")}')
        print(f'  Product Type: {sample.get("product_type", "N/A")}')
        print(f'  Keywords: {sample.get("keywords", [])}')

# -------- PHASE 2: Store Coordinates --------
# Uses Google Geocoding API to get lat/lon for stores based on address.
# Caches results to avoid redundant API calls.

import os
import time
import urllib.request
import urllib.parse

GEOCODE_CACHE_FILE = BASE_DIR / 'geocode_cache.json'

def load_geocode_cache() -> dict:
    """Load cached geocode results."""
    if GEOCODE_CACHE_FILE.exists():
        try:
            with GEOCODE_CACHE_FILE.open('r', encoding='utf-8') as f:
                return json.load(f)
        except Exception:
            return {}
    return {}

def save_geocode_cache(cache: dict):
    """Save geocode cache to disk."""
    try:
        with GEOCODE_CACHE_FILE.open('w', encoding='utf-8') as f:
            json.dump(cache, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f'Warning: failed saving geocode cache: {e}')

def normalize_address(store: dict) -> str:
    """Combine address and location into full address for geocoding."""
    parts = [
        store.get('address', '').strip(),
        store.get('location_text', '').strip()
    ]
    full_address = ', '.join([p for p in parts if p])
    # Collapse multiple spaces
    return ' '.join(full_address.split())

def geocode_address(address: str, api_key: str) -> dict:
    """Call Google Geocoding API to get coordinates."""
    base_url = 'https://maps.googleapis.com/maps/api/geocode/json'
    params = urllib.parse.urlencode({
        'address': address,
        'key': api_key,
        'region': 'ch'  # Hint for Switzerland
    })
    url = f'{base_url}?{params}'
    
    try:
        with urllib.request.urlopen(url, timeout=10) as response:
            data = json.loads(response.read().decode('utf-8'))
    except Exception as e:
        print(f'  Geocoding error for "{address}": {e}')
        return {'latitude': 0.0, 'longitude': 0.0, 'status': 'error'}
    
    status = data.get('status')
    if status != 'OK' or not data.get('results'):
        print(f'  Geocoding failed for "{address}": {status}')
        return {'latitude': 0.0, 'longitude': 0.0, 'status': status}
    
    result = data['results'][0]
    location = result['geometry']['location']
    
    return {
        'latitude': location['lat'],
        'longitude': location['lng'],
        'status': 'OK',
        'accuracy': result['geometry'].get('location_type', 'UNKNOWN')
    }

# This function tries to get coordinates from cache first, then calls API if not found.
# Better to batch process stores to minimize API calls.
def get_coordinates_for_store(store: dict, api_key: str, cache: dict) -> dict:
    """Get coordinates for a store using cache first, then API."""
    address = normalize_address(store)
    
    if not address:
        return {'latitude': 0.0, 'longitude': 0.0}
    
    # Try cache first (exact match)
    if address in cache:
        cached = cache[address]
        return {
            'latitude': cached.get('latitude', 0.0),
            'longitude': cached.get('longitude', 0.0)
        }
    
    # If API key available, call API; otherwise use default
    if api_key:
        result = geocode_address(address, api_key)
        cache[address] = result
        return {
            'latitude': result.get('latitude', 0.0),
            'longitude': result.get('longitude', 0.0)
        }
    else:
        return {'latitude': 0.0, 'longitude': 0.0}

# Change the stores file to have coordinates field with lat/lon
def transform_stores():
    """Phase 2: Restructure coordinates and standardize IDs."""
    if not IN_STORES.exists():
        raise FileNotFoundError(f'{IN_STORES} not found')
    
    with IN_STORES.open('r', encoding='utf-8') as f:
        stores = json.load(f)
    
    # Load geocode cache
    cache = load_geocode_cache()
    
    print(f'  Loaded {len(cache)} cached store coordinates')
    
    api_calls = 0
    cache_hits = 0
    
    for idx, store in enumerate(stores, 1):
        # Remove geo_point field
        if 'geo_point' in store:
            del store['geo_point']
        
        # Extract and add retailer_id
        store_name = store.get('name', '')
        retailer_id = extract_retailer_id(store_name)
        store['retailer_id'] = retailer_id
        
        # Get coordinates from cache
        address = normalize_address(store)
        if address in cache:
            cache_hits += 1
        else:
            api_calls += 1
        
        coords = get_coordinates_for_store(store, None, cache)
        
        if idx % 10 == 0:
            print(f'  Progress: {idx}/{len(stores)} stores processed...')
        
        store['coordinates'] = {
            'latitude': coords['latitude'],
            'longitude': coords['longitude']
        }
        
        # Ensure store_id is in correct format (already should be from source)
        if 'store_id' not in store:
            store['store_id'] = f"unknown_{store.get('name', 'store').lower()}"
    
    with OUT_STORES.open('w', encoding='utf-8') as f:
        json.dump(stores, f, ensure_ascii=False, indent=2)
    
    print(f'✓ Phase 2 complete: {OUT_STORES.name}')
    print(f'  Transformed {len(stores)} stores with coordinates')
    print(f'  Cache hits: {cache_hits}, Cache misses: {api_calls}')
    
    # Count geocoded vs default
    geocoded = sum(1 for s in stores if s['coordinates']['latitude'] != 0.0)
    print(f'  Geocoded: {geocoded}, Default (0.0): {len(stores) - geocoded}')
    
    # Show sample
    if stores:
        sample = stores[0]
        print(f'\n  Sample: {sample.get("name", "?")} - {sample.get("location_text", "?")}')
        print(f'  Coordinates: {sample.get("coordinates", {})}')

# -------- PHASE 3: Retailer-Level Prices --------
# This phase creates retailer-level prices by aggregating store prices per retailer.
from datetime import datetime, timezone

def transform_retailer_prices():
    """Phase 3: Create retailer-level prices (one price per product per retailer)."""
    if not IN_STORE_PRICES.exists():
        raise FileNotFoundError(f'{IN_STORE_PRICES} not found')
    
    # Load store prices
    with IN_STORE_PRICES.open('r', encoding='utf-8') as f:
        inventory = json.load(f)
    
    # Load products for denormalization
    if not OUT_PRODUCTS.exists():
        raise FileNotFoundError(f'{OUT_PRODUCTS} not found. Run Phase 1 first.')
    
    with OUT_PRODUCTS.open('r', encoding='utf-8') as f:
        products = json.load(f)
    
    # Build product lookup map
    product_map = {p['product_id']: p for p in products}
    
    # Get current timestamp
    current_time = datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z')
    
    # Group by retailer_id and product_id
    # Key: (retailer_id, product_id), Value: first price found for that combination
    retailer_prices_map = {}
    
    for item in inventory:
        store_id = item.get('store_id', '')
        product_id = item.get('product_id', '')
        
        # Extract retailer from store_id
        retailer_id = extract_retailer_id(store_id)
        
        # Use first occurrence of each retailer+product combination
        key = (retailer_id, product_id)
        if key not in retailer_prices_map:
            retailer_prices_map[key] = item
    
    # Build transformed list
    transformed = []
    denorm_success = 0
    denorm_missing = 0
    
    for (retailer_id, product_id), item in retailer_prices_map.items():
        # Create document ID: retailer_product
        doc_id = f"{retailer_id}_{product_id}"
        
        # Look up product for denormalization
        product = product_map.get(product_id)
        
        # Build transformed document
        transformed_item = {
            'doc_id': doc_id,
            'retailer_id': retailer_id,
            'product_id': product_id,
            'price': item.get('price'),
            'currency': item.get('currency', 'CHF'),
            'in_stock': item.get('in_stock', False), # if in_stock field exists otherwise False
            'discount_active': item.get('discount_active', False),
        }
        
        # Denormalize product data
        if product:
            transformed_item['product_name'] = product.get('name')
            transformed_item['product_image'] = product.get('image_url')
            transformed_item['category'] = product.get('category')
            transformed_item['product_type'] = product.get('product_type')
            transformed_item['keywords'] = product.get('keywords', [])
            transformed_item['unit'] = product.get('general_unit')
            denorm_success += 1
        else:
            transformed_item['product_name'] = None
            transformed_item['product_image'] = None
            transformed_item['category'] = None
            transformed_item['product_type'] = None
            transformed_item['keywords'] = []
            transformed_item['unit'] = None
            denorm_missing += 1
        
        # Add timestamp
        transformed_item['last_updated'] = current_time
        
        transformed.append(transformed_item)
    
    # Sort by retailer_id then product_id for readability
    transformed.sort(key=lambda x: (x['retailer_id'], x['product_id']))
    
    # Save transformed retailer prices
    with OUT_RETAILER_PRICES.open('w', encoding='utf-8') as f:
        json.dump(transformed, f, ensure_ascii=False, indent=2)
    
    print(f'✓ Phase 3B complete: {OUT_RETAILER_PRICES.name}')
    print(f'  Transformed {len(transformed)} retailer-level prices')
    print(f'  Retailers: {len(set(x["retailer_id"] for x in transformed))}')
    print(f'  Denormalized: {denorm_success}, Missing products: {denorm_missing}')
    print(f'  Timestamp: {current_time}')
    
    # Show sample per retailer
    retailers_seen = set()
    for item in transformed:
        retailer_id = item['retailer_id']
        if retailer_id not in retailers_seen:
            print(f'\n  Sample ({retailer_id}): {item.get("doc_id")}')
            print(f'    Product: {item.get("product_name")}')
            print(f'    Price: {item.get("price")} {item.get("currency")}')
            retailers_seen.add(retailer_id)
            if len(retailers_seen) >= 3:  # Show max 3 samples
                break

# -------- PHASE 4: Clean Discounts --------
# This phase cleans special characters in discounts file.

def transform_discounts():
    """Phase 4: Clean special characters in discounts file."""
    if not OUT_DISCOUNTS.exists():
        print(f' Discounts file not found: {OUT_DISCOUNTS}')
        return
    
    with OUT_DISCOUNTS.open('r', encoding='utf-8') as f:
        discounts = json.load(f)
    
    # Clean special characters from product names
    for discount in discounts:
        if 'product_name' in discount and discount['product_name']:
            discount['product_name'] = clean_special_chars(discount['product_name'])
    
    # Save cleaned discounts
    with OUT_DISCOUNTS.open('w', encoding='utf-8') as f:
        json.dump(discounts, f, ensure_ascii=False, indent=2)
    
    print(f' Phase 4 complete: {OUT_DISCOUNTS.name}')
    print(f' Cleaned {len(discounts)} discount entries')
    
    # Show sample
    if discounts:
        sample = discounts[0]
        print(f'\n  Sample: {sample.get("product_name")}')
        print(f'  Discount: {sample.get("discount_percentage")}%')
def main():
    print('=== Firestore Transformation ===\n')
    
    # Phase 0: Retailers
    create_retailers()
    
    print() # Newline for separation
    
    # Phase 1: Products
    transform_products()
    
    print()
    
    # Phase 2
    transform_stores()
    
    print()    
   
    # Phase 3: Retailer-level prices (NEW)
    transform_retailer_prices()
    
    print()
    
    # Phase 4
    transform_discounts()
    
    print('\n=== Transformation Complete ===')
    print(f'Retailers: {OUT_RETAILERS}')
    print(f'Products: {OUT_PRODUCTS}')
    print(f'Stores: {OUT_STORES}')
    print(f'Prices (retailer-level): {OUT_RETAILER_PRICES}')
    print(f'Discounts: {OUT_DISCOUNTS}')

if __name__ == '__main__':
    main()
