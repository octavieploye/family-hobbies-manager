/**
 * Centralized test data for all E2E specs.
 *
 * This data matches the seed data loaded by docker-compose.e2e.yml
 * into the PostgreSQL databases. All names, cities, and associations
 * use realistic French data.
 *
 * IMPORTANT: If you change this file, also update the SQL seed scripts
 * in docker/init-scripts/e2e-seed-*.sql to stay in sync.
 */

// -- Users ---------------------------------------------------------------

export const TEST_USERS = {
  /** Pre-seeded family user -- exists in DB on startup */
  familyUser: {
    email: 'famille.dupont@test.familyhobbies.fr',
    password: 'Test1234!',
    firstName: 'Marie',
    lastName: 'Dupont',
    role: 'FAMILY' as const,
  },

  /** Pre-seeded association admin user */
  associationAdmin: {
    email: 'admin.assolyonnaise@test.familyhobbies.fr',
    password: 'Test1234!',
    firstName: 'Jean',
    lastName: 'Martin',
    role: 'ASSOCIATION' as const,
  },

  /** Pre-seeded platform admin */
  admin: {
    email: 'admin@test.familyhobbies.fr',
    password: 'Admin1234!',
    firstName: 'Philippe',
    lastName: 'Leroy',
    role: 'ADMIN' as const,
  },

  /** New user for registration tests -- does NOT exist in DB */
  newUser: {
    email: `test.nouveau.${Date.now()}@test.familyhobbies.fr`,
    password: 'Nouveau1234!',
    firstName: 'Sophie',
    lastName: 'Bernard',
    role: 'FAMILY' as const,
  },

  /** User with invalid credentials -- for error testing */
  invalidUser: {
    email: 'inexistant@test.familyhobbies.fr',
    password: 'MauvaisMotDePasse!',
  },
};

// -- Families ------------------------------------------------------------

export const TEST_FAMILIES = {
  /** Pre-seeded family with 3 members */
  dupont: {
    id: '550e8400-e29b-41d4-a716-446655440001',
    name: 'Famille Dupont',
    members: [
      {
        firstName: 'Marie',
        lastName: 'Dupont',
        birthDate: '1985-03-15',
        role: 'Parent',
      },
      {
        firstName: 'Lucas',
        lastName: 'Dupont',
        birthDate: '2012-07-22',
        role: 'Enfant',
      },
      {
        firstName: 'Emma',
        lastName: 'Dupont',
        birthDate: '2015-11-08',
        role: 'Enfant',
      },
    ],
  },

  /** Data for creating a new family in tests */
  newFamily: {
    name: 'Famille Moreau',
    members: [
      {
        firstName: 'Claire',
        lastName: 'Moreau',
        birthDate: '1990-06-20',
        role: 'Parent',
      },
    ],
  },

  /** Data for adding a new member */
  newMember: {
    firstName: 'Hugo',
    lastName: 'Dupont',
    birthDate: '2018-01-30',
    role: 'Enfant',
  },
};

// -- Associations --------------------------------------------------------

export const TEST_ASSOCIATIONS = {
  /** Pre-seeded sport association in Lyon */
  sportLyon: {
    id: '660e8400-e29b-41d4-a716-446655440010',
    name: 'Association Sportive de Lyon',
    slug: 'association-sportive-de-lyon',
    city: 'Lyon',
    postalCode: '69001',
    category: 'Sport',
    activities: ['Football', 'Basketball', 'Gymnastique'],
  },

  /** Pre-seeded dance school in Paris */
  danseParis: {
    id: '660e8400-e29b-41d4-a716-446655440011',
    name: 'Ecole de Danse Classique de Paris',
    slug: 'ecole-danse-classique-paris',
    city: 'Paris',
    postalCode: '75004',
    category: 'Danse',
    activities: ['Danse classique', 'Modern jazz'],
  },

  /** Pre-seeded music conservatory in Toulouse */
  musiqueToulouse: {
    id: '660e8400-e29b-41d4-a716-446655440012',
    name: 'Conservatoire Municipal de Musique de Toulouse',
    slug: 'conservatoire-musique-toulouse',
    city: 'Toulouse',
    postalCode: '31000',
    category: 'Musique',
    activities: ['Piano', 'Violon', 'Chorale'],
  },

  /** Association that returns no search results (for empty state tests) */
  nonExistent: {
    keyword: 'ZzzAssociationInexistante999',
    city: 'VilleImaginaire',
  },
};

// -- Subscriptions -------------------------------------------------------

export const TEST_SUBSCRIPTIONS = {
  /** Pre-seeded active subscription: Lucas Dupont -> Football @ Lyon */
  lucasFootball: {
    id: '770e8400-e29b-41d4-a716-446655440020',
    memberName: 'Lucas Dupont',
    associationName: 'Association Sportive de Lyon',
    activityName: 'Football',
    status: 'ACTIVE',
  },

  /** Pre-seeded active subscription: Emma Dupont -> Danse classique @ Paris */
  emmaDanse: {
    id: '770e8400-e29b-41d4-a716-446655440021',
    memberName: 'Emma Dupont',
    associationName: 'Ecole de Danse Classique de Paris',
    activityName: 'Danse classique',
    status: 'ACTIVE',
  },
};

// -- Payments ------------------------------------------------------------

export const TEST_PAYMENTS = {
  /** Pre-seeded completed payment */
  completedPayment: {
    id: '880e8400-e29b-41d4-a716-446655440030',
    amount: '120.00',
    status: 'COMPLETED',
    associationName: 'Association Sportive de Lyon',
    method: 'CARD',
  },

  /** Pre-seeded pending payment (awaiting webhook) */
  pendingPayment: {
    id: '880e8400-e29b-41d4-a716-446655440031',
    amount: '85.00',
    status: 'PENDING',
    associationName: 'Ecole de Danse Classique de Paris',
    method: 'CARD',
  },
};

// -- Notifications -------------------------------------------------------

export const TEST_NOTIFICATIONS = {
  /** Expected minimum number of unread notifications for familyUser */
  expectedUnreadCount: 3,

  /** Notification types that should appear */
  expectedTypes: [
    'SUBSCRIPTION_CONFIRMED',
    'PAYMENT_COMPLETED',
    'SESSION_REMINDER',
  ],
};

// -- API Configuration ---------------------------------------------------

export const API_CONFIG = {
  baseUrl: process.env.API_URL || 'http://localhost:8080',
  timeout: 10000,
};
