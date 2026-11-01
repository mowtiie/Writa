-- ============================================================
-- Writa: database schema (v1.0)
-- Run this once in your own Supabase project:
--   Dashboard > SQL Editor > New query > paste > Run.
--
-- It is safe to run again. The app asks you to run it again when a
-- column is missing, and re-running adds whatever is absent.
-- ============================================================

-- 1. Notes table.
--    user_id is filled from the signed-in user's token (auth.uid()),
--    so the app never sends it.
create table if not exists public.notes (
  id         bigint generated always as identity primary key,
  user_id    uuid not null default auth.uid()
                  references auth.users (id) on delete cascade,
  title      text not null,
  content    text,
  tags       text[] not null default '{}',
  pinned     boolean not null default false,
  archived   boolean not null default false,
  created_at timestamptz default now(),
  updated_at timestamptz default now(),
  deleted_at timestamptz,
  client_id  uuid
);

-- 1b. Adds any column an existing, incomplete table lacks.
alter table public.notes add column if not exists content text;
alter table public.notes add column if not exists tags text[] not null default '{}';
alter table public.notes add column if not exists pinned boolean not null default false;
alter table public.notes add column if not exists archived boolean not null default false;
alter table public.notes add column if not exists created_at timestamptz default now();
alter table public.notes add column if not exists updated_at timestamptz default now();
alter table public.notes add column if not exists deleted_at timestamptz;
alter table public.notes add column if not exists client_id uuid;
alter table public.notes alter column updated_at set default now();

-- 2. client_id is made by the app for notes created offline, so a retried
--    upload cannot create the same note twice (the second insert is a
--    unique violation, and the app looks the note up by client_id).
create unique index if not exists notes_client_id_key
  on public.notes (client_id);

-- The app lists a user's notes newest first.
create index if not exists notes_user_created_idx
  on public.notes (user_id, created_at desc, id desc);

-- 3. updated_at changes only when the text changes. Pinning, archiving,
--    trashing and restoring leave it alone, so they never make a note look
--    edited, and the app can use it to detect conflicting text edits.
create or replace function public.notes_set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if new.title is distinct from old.title
     or new.content is distinct from old.content then
    new.updated_at = now();
  else
    new.updated_at = old.updated_at;
  end if;
  return new;
end;
$$;

drop trigger if exists notes_set_updated_at on public.notes;
create trigger notes_set_updated_at
  before update on public.notes
  for each row execute function public.notes_set_updated_at();

-- 4. Row Level Security: every user can read and write only their own notes.
alter table public.notes enable row level security;

drop policy if exists "notes_select_own" on public.notes;
create policy "notes_select_own" on public.notes
  for select to authenticated
  using ((select auth.uid()) = user_id);

drop policy if exists "notes_insert_own" on public.notes;
create policy "notes_insert_own" on public.notes
  for insert to authenticated
  with check ((select auth.uid()) = user_id);

drop policy if exists "notes_update_own" on public.notes;
create policy "notes_update_own" on public.notes
  for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);

drop policy if exists "notes_delete_own" on public.notes;
create policy "notes_delete_own" on public.notes
  for delete to authenticated
  using ((select auth.uid()) = user_id);

-- 5. Only signed-in users may use the table; the public (anon) key alone
--    cannot touch it.
revoke all on public.notes from anon;
grant select, insert, update, delete on public.notes to authenticated;

-- ============================================================
-- One-time dashboard setting (not SQL, do this in the UI):
--   Authentication > Sign In / Providers > Email: enable it.
--   If "Confirm email" is on, new accounts must confirm by email
--   before they can sign in; the app tells them to check their inbox.
-- ============================================================
