# Helpful Commands
At first load: run `docker compose up -d --build` in root directory

When working on the React frontend can just do `docker compose up -d keycloak postgres-db` to just start 2 of the images and not the react image

Would also need to do cd into frontend-ui directory and do `npm run dev` to get React app running on localhost:5173

When changing keycloak realm JSON file, do `docker compose down -v` to reset persistent data so that realm will be rebuilt

# To Do
- Token service
- API Gateway
- API GET call for account list for Alice
- Add StepCards for showing accounts list twice (once before revoking and once after revoking)
- Add tooltips indicating tech stack info