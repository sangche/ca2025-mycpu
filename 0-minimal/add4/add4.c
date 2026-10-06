__attribute__((noinline))
int add4(int a)
{
    return a + 4;
}

int main()
{
    int a = 3;
    int c = add4(a);

    return c;
}