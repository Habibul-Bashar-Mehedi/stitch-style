-- The storefront links to /collections/men and /collections/women.
-- Ensure those collection roots always exist, even on a database that was
-- seeded before the demo catalogue was expanded.
INSERT INTO categories (name, slug, description, size_type, display_order, depth, is_active, show_on_home, show_in_navbar)
VALUES ('Men', 'men', 'Panjabi, shirts and festive menswear.', 'LETTER', 0, 0, true, true, true)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO categories (name, slug, description, size_type, display_order, depth, is_active, show_on_home, show_in_navbar)
VALUES ('Women', 'women', 'Sharee, three-piece and everyday womenswear.', 'LETTER', 1, 0, true, true, true)
ON CONFLICT (slug) DO NOTHING;
