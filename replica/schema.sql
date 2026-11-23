-- schema.sql: backend awan untuk klon StreamingIn (Supabase Postgres, region Singapura)
-- Prinsip: LOCAL-FIRST. Scene, elemen, stream key, dan log event ada di perangkat (Room/SQLite).
-- Database ini hanya memegang: akun, Pro, Coin, Store, media cloud, endpoint donasi, rilis app.
-- Waktu selalu timestamptz (UTC). Uang = integer + kolom currency. Akses = Row Level Security (RLS).
-- Catatan: belum dijalankan pada database nyata; uji di proyek Supabase kosong sebelum dipakai.

create extension if not exists pgcrypto;

create or replace function set_updated_at() returns trigger
language plpgsql as $$
begin new.updated_at = now(); return new; end $$;

-- 1. konfigurasi (angka bisnis yang bertentangan di sumber disimpan di sini, bukan di kode)
create table app_config (
  key text primary key,
  value jsonb not null,
  updated_at timestamptz not null default now()
);
insert into app_config(key, value) values
  ('creator_share_pct', '75'),
  ('payout_min_sc', '100'),
  ('sc_price_usd_cents', '10'),
  ('free_limits', '{"scenes":3,"elements_per_scene":7,"broadcast_connections":1,"event_connections":1}'),
  ('promo_pro_free_until', '"2026-12-31T16:59:59Z"');  -- "Free Like a Pro" sampai akhir 2026 (WIB); cek ulang

-- 2. profil (id = auth.users.id; pengecualian dari default uuid karena 1:1 dengan auth)
create table profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text,
  avatar_url text,
  locale text not null default 'id' check (locale in ('id','en')),
  creator_verified boolean not null default false,
  referral_code text unique,
  referred_by uuid references profiles(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index on profiles (referred_by);

-- 3. katalog produk (Pro dan paket Coin). Harga dalam Rupiah utuh (IDR tanpa sen).
create table products (
  sku text primary key,
  kind text not null check (kind in ('pro','coins')),
  name text not null,
  price_amount integer not null check (price_amount >= 0),
  currency text not null default 'IDR',
  duration_days integer check (duration_days > 0),
  coins integer check (coins > 0),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check ((kind = 'pro' and duration_days is not null and coins is null)
      or (kind = 'coins' and coins is not null and duration_days is null))
);

-- 4. voucher referral (cara kerja komisi = TEBAKAN; konfirmasi sebelum dibangun)
create table vouchers (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  owner_id uuid references profiles(id) on delete set null,
  discount_amount integer not null check (discount_amount >= 0),
  commission_sc integer not null default 0 check (commission_sc >= 0),
  max_uses integer check (max_uses > 0),
  uses integer not null default 0 check (uses >= 0),
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (max_uses is null or uses <= max_uses)
);
create index on vouchers (owner_id);

-- 5. pesanan. on delete restrict: catatan keuangan tidak ikut terhapus bersama pengguna;
--    saat akun dihapus, user_id dianonimkan oleh fungsi hapus akun (lihat F10), bukan lewat cascade.
create table orders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references profiles(id) on delete restrict,
  sku text not null references products(sku) on delete restrict,
  amount integer not null check (amount >= 0),
  currency text not null default 'IDR',
  voucher_id uuid references vouchers(id) on delete set null,
  provider text not null check (provider in ('midtrans','xendit','play_billing','manual')),
  provider_ref text,
  status text not null default 'pending'
    check (status in ('pending','paid','failed','expired','refunded')),
  paid_at timestamptz,
  expires_at timestamptz not null default (now() + interval '1 day'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (provider, provider_ref)
);
create index on orders (user_id, created_at desc);
create index on orders (status, expires_at) where status = 'pending';
create index on orders (voucher_id);

create table voucher_redemptions (
  id uuid primary key default gen_random_uuid(),
  voucher_id uuid not null references vouchers(id) on delete cascade,
  order_id uuid not null unique references orders(id) on delete cascade,
  created_at timestamptz not null default now()
);
create index on voucher_redemptions (voucher_id);

-- 6. hak akses Pro: satu baris per periode; perpanjangan = baris baru
create table pro_entitlements (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references profiles(id) on delete cascade,
  plan text not null check (plan in ('pro_30d','pro_360d','promo')),
  source text not null check (source in ('payment','voucher','promo','admin')),
  order_id uuid references orders(id) on delete set null,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (ends_at > starts_at),
  unique (order_id)   -- webhook pembayaran yang datang dua kali tidak memberi Pro dua kali
);
create index on pro_entitlements (user_id, ends_at desc);

create or replace function is_pro(p_user uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from pro_entitlements
    where user_id = p_user and now() >= starts_at and now() < ends_at
  );
$$;

-- 7. Store: produk
create table store_listings (
  id uuid primary key default gen_random_uuid(),
  creator_id uuid not null references profiles(id) on delete restrict,
  title text not null check (char_length(title) between 3 and 80),
  description text not null default '',
  category text not null check (category in ('gaming','business','education','podcast','other')),
  tags text[] not null default '{}',
  price_sc integer not null default 0 check (price_sc >= 0),
  canvas text not null check (canvas in ('9:16','16:9')),
  scene_path text not null,             -- file snapshot scene di Storage (bucket privat)
  version integer not null default 1,
  is_official boolean not null default false,   -- template resmi gratis
  status text not null default 'draft'
    check (status in ('draft','in_review','published','rejected','removed')),
  review_note text,
  published_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index on store_listings (creator_id);
create index on store_listings (status, published_at desc);
create index on store_listings (category, status);
create index on store_listings using gin (tags);

create table store_listing_media (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references store_listings(id) on delete cascade,
  kind text not null check (kind in ('preview','screenshot','demo_video','thumbnail')),
  storage_path text not null,
  position integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index on store_listing_media (listing_id, position);

-- 8. Store: pembelian dan ulasan
create table store_purchases (
  id uuid primary key default gen_random_uuid(),
  buyer_id uuid not null references profiles(id) on delete restrict,
  listing_id uuid not null references store_listings(id) on delete restrict,
  listing_version integer not null,
  price_sc integer not null check (price_sc >= 0),
  creator_share_sc integer not null check (creator_share_sc >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (buyer_id, listing_id)        -- tidak bisa beli dua kali
);
create index on store_purchases (listing_id);

create table store_reviews (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references store_listings(id) on delete cascade,
  user_id uuid not null references profiles(id) on delete cascade,
  rating smallint not null check (rating between 1 and 5),
  body text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (listing_id, user_id)
);
create index on store_reviews (user_id);

-- 9. buku besar Coin: hanya tambah baris, tidak pernah ubah. Saldo = jumlah delta.
create table coin_ledger (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references profiles(id) on delete restrict,
  delta integer not null check (delta <> 0),
  reason text not null check (reason in
    ('purchase','store_buy','store_sale','commission','refund','payout','admin')),
  order_id uuid references orders(id) on delete restrict,
  purchase_id uuid references store_purchases(id) on delete restrict,
  created_at timestamptz not null default now()
);
create index on coin_ledger (user_id, created_at desc);
create index on coin_ledger (order_id);
create index on coin_ledger (purchase_id);
create unique index coin_ledger_one_per_purchase
  on coin_ledger (purchase_id, user_id, reason) where purchase_id is not null;
create unique index coin_ledger_one_per_order
  on coin_ledger (order_id, reason) where order_id is not null;

create view coin_balances with (security_invoker = true) as
  select user_id, coalesce(sum(delta), 0)::integer as balance
  from coin_ledger group by user_id;

create table creator_payouts (
  id uuid primary key default gen_random_uuid(),
  creator_id uuid not null references profiles(id) on delete restrict,
  amount_sc integer not null check (amount_sc > 0),
  amount_idr integer not null check (amount_idr >= 0),
  method text not null check (method in ('bank_transfer','ewallet')),
  status text not null default 'requested'
    check (status in ('requested','approved','paid','rejected')),
  period date not null,                 -- awal bulan (WIB)
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index on creator_payouts (creator_id, period desc);
create index on creator_payouts (status);

-- 10. media cloud (SFX bersama milik sistem: owner_id null)
create table cloud_media (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid references profiles(id) on delete cascade,
  kind text not null check (kind in ('sfx','image','video','font')),
  name text not null,
  storage_path text not null,
  bytes integer not null check (bytes > 0 and bytes <= 52428800),  -- 50 MB; sesuaikan
  mime text not null,
  pro_only boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index on cloud_media (owner_id, kind);

-- 11. endpoint donasi: token publik ada di URL webhook; rahasia penanda tangan di Supabase Vault
create table donation_endpoints (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references profiles(id) on delete cascade,
  provider text not null check (provider in
    ('saweria','sociabuzz','trakteer','tako','bagibagi','sibagi','tiptap')),
  public_token text not null unique default encode(gen_random_bytes(24), 'hex'),
  vault_secret_id uuid,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, provider)
);

-- 12. idempotensi webhook: simpan ID kejadian saja, BUKAN isi (isi donasi tidak disimpan)
create table webhook_events (
  id uuid primary key default gen_random_uuid(),
  source text not null,                 -- 'midtrans' | 'xendit' | 'donation:<provider>'
  event_id text not null,
  status text not null default 'processed' check (status in ('processed','rejected','ignored')),
  received_at timestamptz not null default now(),
  unique (source, event_id)
);
create index on webhook_events (received_at);

-- 13. rilis app untuk update dari dalam app
create table app_releases (
  id uuid primary key default gen_random_uuid(),
  version_code integer not null,
  version_name text not null,
  channel text not null default 'stable' check (channel in ('stable','beta')),
  apk_url text not null,
  apk_sha256 text not null check (apk_sha256 ~ '^[0-9a-f]{64}$'),
  min_sdk integer not null default 26,
  notes text not null default '',
  published_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (channel, version_code)
);

-- pemicu updated_at untuk semua tabel yang punya kolomnya
do $$
declare t record;
begin
  for t in
    select table_name from information_schema.columns
    where table_schema = 'public' and column_name = 'updated_at'
      and table_name in (select table_name from information_schema.tables
                         where table_schema = 'public' and table_type = 'BASE TABLE')
  loop
    execute format('create trigger set_updated_at before update on %I
                    for each row execute function set_updated_at()', t.table_name);
  end loop;
end $$;

-- beli desain Store: satu transaksi, kunci per pembeli, tidak bisa minus atau beli dua kali
create or replace function buy_listing(p_listing_id uuid) returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_user uuid := auth.uid();
  v_listing store_listings%rowtype;
  v_balance integer;
  v_pct integer;
  v_share integer;
  v_purchase uuid;
begin
  if v_user is null then raise exception 'not authenticated'; end if;

  select * into v_listing from store_listings
   where id = p_listing_id and status = 'published';
  if not found then raise exception 'listing not available'; end if;
  if v_listing.creator_id = v_user then raise exception 'cannot buy own listing'; end if;

  perform pg_advisory_xact_lock(hashtextextended(v_user::text, 0));

  if exists (select 1 from store_purchases
              where buyer_id = v_user and listing_id = p_listing_id) then
    raise exception 'already owned';
  end if;

  select coalesce(sum(delta), 0) into v_balance from coin_ledger where user_id = v_user;
  if v_balance < v_listing.price_sc then raise exception 'insufficient coins'; end if;

  select (value #>> '{}')::integer into v_pct from app_config where key = 'creator_share_pct';
  v_share := floor(v_listing.price_sc * v_pct / 100.0);

  insert into store_purchases(buyer_id, listing_id, listing_version, price_sc, creator_share_sc)
  values (v_user, p_listing_id, v_listing.version, v_listing.price_sc, v_share)
  returning id into v_purchase;

  if v_listing.price_sc > 0 then
    insert into coin_ledger(user_id, delta, reason, purchase_id)
    values (v_user, -v_listing.price_sc, 'store_buy', v_purchase);
    if v_share > 0 then
      insert into coin_ledger(user_id, delta, reason, purchase_id)
      values (v_listing.creator_id, v_share, 'store_sale', v_purchase);
    end if;
  end if;
  return v_purchase;
end $$;
revoke all on function buy_listing(uuid) from public;
grant execute on function buy_listing(uuid) to authenticated;

-- ===== RLS =====
-- Aturan: tulis ke uang, Pro, dan webhook HANYA lewat service role / fungsi security definer.
alter table profiles            enable row level security;
alter table products            enable row level security;
alter table vouchers            enable row level security;
alter table orders              enable row level security;
alter table voucher_redemptions enable row level security;
alter table pro_entitlements    enable row level security;
alter table store_listings      enable row level security;
alter table store_listing_media enable row level security;
alter table store_purchases     enable row level security;
alter table store_reviews       enable row level security;
alter table coin_ledger         enable row level security;
alter table creator_payouts     enable row level security;
alter table cloud_media         enable row level security;
alter table donation_endpoints  enable row level security;
alter table webhook_events      enable row level security;   -- tanpa policy = hanya service role
alter table app_releases        enable row level security;
alter table app_config          enable row level security;

create policy profiles_own_read   on profiles for select using (id = auth.uid());
create policy profiles_own_update on profiles for update using (id = auth.uid())
  with check (id = auth.uid());
-- kolom berhak istimewa tidak boleh diubah pengguna sendiri (hanya service role / admin)
create or replace function lock_profile_privileged() returns trigger
language plpgsql as $$
begin
  if auth.role() = 'authenticated' then
    new.creator_verified := old.creator_verified;
    new.referred_by := old.referred_by;
    new.referral_code := old.referral_code;
  end if;
  return new;
end $$;
create trigger lock_profile_privileged before update on profiles
  for each row execute function lock_profile_privileged();

-- profil dibuat otomatis saat pendaftaran (login Google pertama)
create or replace function handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into profiles(id, display_name, avatar_url, referral_code)
  values (new.id,
          coalesce(new.raw_user_meta_data->>'full_name', split_part(new.email,'@',1)),
          new.raw_user_meta_data->>'avatar_url',
          encode(gen_random_bytes(5), 'hex'))
  on conflict (id) do nothing;
  return new;
end $$;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function handle_new_user();

create policy products_read on products for select using (active);
create policy app_config_read on app_config for select using (key in ('free_limits','sc_price_usd_cents'));
create policy releases_read on app_releases for select using (published_at is not null);

create policy vouchers_owner_read on vouchers for select using (owner_id = auth.uid());
create policy orders_own_read on orders for select using (user_id = auth.uid());
create policy redemptions_own_read on voucher_redemptions for select
  using (exists (select 1 from orders o where o.id = order_id and o.user_id = auth.uid()));
create policy entitlements_own_read on pro_entitlements for select using (user_id = auth.uid());
create policy ledger_own_read on coin_ledger for select using (user_id = auth.uid());
create policy payouts_own_read on creator_payouts for select using (creator_id = auth.uid());

create policy listings_read on store_listings for select
  using (status = 'published' or creator_id = auth.uid());
create policy listings_insert on store_listings for insert
  with check (creator_id = auth.uid() and status = 'draft' and is_pro(auth.uid())
              and exists (select 1 from profiles p where p.id = auth.uid() and p.creator_verified));
create policy listings_update_own on store_listings for update
  using (creator_id = auth.uid() and status in ('draft','rejected'))
  with check (creator_id = auth.uid() and status in ('draft','in_review'));
create policy media_read on store_listing_media for select
  using (exists (select 1 from store_listings l where l.id = listing_id
                 and (l.status = 'published' or l.creator_id = auth.uid())));
create policy media_write_own on store_listing_media for all
  using (exists (select 1 from store_listings l where l.id = listing_id and l.creator_id = auth.uid()))
  with check (exists (select 1 from store_listings l where l.id = listing_id and l.creator_id = auth.uid()));

create policy purchases_own_read on store_purchases for select
  using (buyer_id = auth.uid()
         or exists (select 1 from store_listings l where l.id = listing_id and l.creator_id = auth.uid()));
create policy reviews_read on store_reviews for select using (true);
create policy reviews_write_own on store_reviews for insert
  with check (user_id = auth.uid()
              and exists (select 1 from store_purchases sp
                          where sp.listing_id = store_reviews.listing_id and sp.buyer_id = auth.uid()));
create policy reviews_update_own on store_reviews for update
  using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy media_cloud_read on cloud_media for select
  using ((owner_id is null and (not pro_only or is_pro(auth.uid()))) or owner_id = auth.uid());
create policy media_cloud_delete_own on cloud_media for delete using (owner_id = auth.uid());
-- upload lewat Edge Function (cek Pro dan ukuran); tidak ada policy insert langsung.

create policy donation_own_read on donation_endpoints for select using (user_id = auth.uid());
create policy donation_own_delete on donation_endpoints for delete using (user_id = auth.uid());
-- pembuatan endpoint lewat Edge Function (membuat rahasia di Vault).
