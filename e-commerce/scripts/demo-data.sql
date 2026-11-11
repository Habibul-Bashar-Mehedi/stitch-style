-- Demo catalogue, delivery areas and settings for local review.
-- Apply after the application has run Flyway once:
--   docker exec -i bigtheta-db-postgresql-latest-container psql -U lemon -d stitchstyle < e-commerce/scripts/demo-data.sql

INSERT INTO users (full_name, email, role, is_active)
VALUES ('Demo Admin', 'admin@example.com', 'ADMIN', true)
ON CONFLICT DO NOTHING;

INSERT INTO categories (name, slug, description, size_type, display_order, depth, is_active, show_on_home, show_in_navbar)
VALUES ('Men', 'men', 'Panjabi, shirts and festive menswear.', 'LETTER', 0, 0, true, true, true)
ON CONFLICT (slug) DO NOTHING;
INSERT INTO categories (name, slug, description, size_type, display_order, depth, is_active, show_on_home, show_in_navbar)
VALUES ('Women', 'women', 'Sharee, three-piece and everyday womenswear.', 'LETTER', 1, 0, true, true, true)
ON CONFLICT (slug) DO NOTHING;
INSERT INTO categories (name, slug, description, size_type, display_order, depth, is_active, show_on_home, show_in_navbar)
VALUES ('Jewelry', 'jewelry', 'Festive and everyday jewelry.', 'FREE', 2, 0, true, true, true)
ON CONFLICT (slug) DO NOTHING;
INSERT INTO categories (name, slug, description, size_type, display_order, depth, is_active, show_on_home, show_in_navbar, parent_id)
SELECT 'Panjabi', 'men-panjabi', 'Festive and formal panjabi.', 'LETTER', 0, 1, true, false, false, id
FROM categories WHERE slug = 'men'
ON CONFLICT (slug) DO NOTHING;
INSERT INTO categories (name, slug, description, size_type, display_order, depth, is_active, show_on_home, show_in_navbar, parent_id)
SELECT 'Sharee', 'women-sharee', 'Handloom and festive sharee.', 'FREE', 0, 1, true, false, false, id
FROM categories WHERE slug = 'women'
ON CONFLICT (slug) DO NOTHING;

-- Older demo seeds created these as top-level categories. Link them to their
-- collection so /collections/men and /collections/women include their products.
UPDATE categories child
SET parent_id = (SELECT id FROM categories WHERE slug = 'men'), depth = 1
WHERE child.slug IN ('shirt', 'panjabi') AND child.parent_id IS NULL;
UPDATE categories child
SET parent_id = (SELECT id FROM categories WHERE slug = 'women'), depth = 1
WHERE child.slug IN ('sharee', 'kurti') AND child.parent_id IS NULL;

INSERT INTO products (category_id, name_i18n, slug, description_i18n, base_price, discount_price, size_type, origin_country, brand, model, delivery_estimate, is_active, is_featured)
SELECT id, '{"en":"Classic Cotton Panjabi","bn":"ক্লাসিক কটন পাঞ্জাবি"}'::jsonb, 'classic-cotton-panjabi',
       '{"en":"A breathable cotton panjabi for festive and everyday wear.","bn":"উৎসব ও প্রতিদিনের জন্য আরামদায়ক কটন পাঞ্জাবি।"}'::jsonb,
       2490.00, 2190.00, 'LETTER', 'Bangladesh', 'Stitch & Style', 'PJ-101', '2–4 days', true, true
FROM categories WHERE slug = 'men-panjabi'
ON CONFLICT (slug) DO NOTHING;
INSERT INTO products (category_id, name_i18n, slug, description_i18n, base_price, discount_price, size_type, origin_country, brand, model, delivery_estimate, is_active, is_featured)
SELECT id, '{"en":"Handloom Cotton Sharee","bn":"হ্যান্ডলুম কটন শাড়ি"}'::jsonb, 'handloom-cotton-sharee',
       '{"en":"Soft handloom sharee with a classic border.","bn":"ক্লাসিক পাড়ের নরম হ্যান্ডলুম শাড়ি।"}'::jsonb,
       3490.00, NULL, 'FREE', 'Bangladesh', 'Stitch & Style', 'SH-202', '3–5 days', true, true
FROM categories WHERE slug = 'women-sharee'
ON CONFLICT (slug) DO NOTHING;
INSERT INTO products (category_id, name_i18n, slug, description_i18n, base_price, discount_price, size_type, origin_country, brand, model, delivery_estimate, is_active, is_featured)
SELECT id, '{"en":"Festive Three-Piece","bn":"উৎসব থ্রি-পিস"}'::jsonb, 'festive-three-piece',
       '{"en":"A coordinated three-piece set for festive occasions.","bn":"উৎসবের জন্য সমন্বিত থ্রি-পিস।"}'::jsonb,
       4290.00, 3990.00, 'LETTER', 'Bangladesh', 'Stitch & Style', 'TP-303', '3–5 days', true, true
FROM categories WHERE slug = 'women'
ON CONFLICT (slug) DO NOTHING;

INSERT INTO product_variants (product_id, size_label, sku, stock_quantity, reserved_quantity, low_stock_threshold, is_active)
SELECT id, 'M', 'PJ-101-M', 8, 0, 2, true FROM products WHERE slug = 'classic-cotton-panjabi' ON CONFLICT (sku) DO NOTHING;
INSERT INTO product_variants (product_id, size_label, sku, stock_quantity, reserved_quantity, low_stock_threshold, is_active)
SELECT id, 'L', 'PJ-101-L', 10, 0, 2, true FROM products WHERE slug = 'classic-cotton-panjabi' ON CONFLICT (sku) DO NOTHING;
INSERT INTO product_variants (product_id, size_label, sku, stock_quantity, reserved_quantity, low_stock_threshold, is_active)
SELECT id, 'XL', 'PJ-101-XL', 4, 0, 2, true FROM products WHERE slug = 'classic-cotton-panjabi' ON CONFLICT (sku) DO NOTHING;
INSERT INTO product_variants (product_id, size_label, sku, stock_quantity, reserved_quantity, low_stock_threshold, is_active)
SELECT id, 'Free Size', 'SH-202-F', 6, 0, 2, true FROM products WHERE slug = 'handloom-cotton-sharee' ON CONFLICT (sku) DO NOTHING;
INSERT INTO product_variants (product_id, size_label, sku, stock_quantity, reserved_quantity, low_stock_threshold, is_active)
SELECT id, 'M', 'TP-303-M', 5, 0, 2, true FROM products WHERE slug = 'festive-three-piece' ON CONFLICT (sku) DO NOTHING;
INSERT INTO product_variants (product_id, size_label, sku, stock_quantity, reserved_quantity, low_stock_threshold, is_active)
SELECT id, 'L', 'TP-303-L', 5, 0, 2, true FROM products WHERE slug = 'festive-three-piece' ON CONFLICT (sku) DO NOTHING;

INSERT INTO delivery_areas (name, district, delivery_charge, free_shipping_threshold, sla_days, is_active, display_order)
SELECT 'Dhaka City', 'Dhaka', 60.00, 3000.00, 2, true, 0
WHERE NOT EXISTS (SELECT 1 FROM delivery_areas WHERE name = 'Dhaka City');
INSERT INTO delivery_areas (name, district, delivery_charge, free_shipping_threshold, sla_days, is_active, display_order)
SELECT 'Chattogram City', 'Chattogram', 120.00, 4000.00, 3, true, 1
WHERE NOT EXISTS (SELECT 1 FROM delivery_areas WHERE name = 'Chattogram City');
INSERT INTO delivery_areas (name, district, delivery_charge, free_shipping_threshold, sla_days, is_active, display_order)
SELECT 'Outside City', 'Other', 150.00, NULL, 5, true, 2
WHERE NOT EXISTS (SELECT 1 FROM delivery_areas WHERE name = 'Outside City');

INSERT INTO site_settings (setting_key, setting_value, value_type) VALUES
('brand.name', 'Stitch & Style', 'STRING'),
('payment.cod.enabled', 'true', 'BOOLEAN'),
('payment.bkash.enabled', 'true', 'BOOLEAN'),
('payment.sslcommerz.enabled', 'true', 'BOOLEAN'),
('home.hero.mode', 'PINNED_FIRST', 'STRING')
ON CONFLICT (setting_key) DO NOTHING;

INSERT INTO notices (title, body, priority, is_active)
SELECT 'Eid delivery update', 'Order before 4 PM for same-day dispatch inside Dhaka.', 10, true
WHERE NOT EXISTS (SELECT 1 FROM notices WHERE title = 'Eid delivery update');
